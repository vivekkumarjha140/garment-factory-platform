package com.garmentfactory.cart.service;

import com.garmentfactory.cart.entity.Cart;
import com.garmentfactory.cart.entity.CartItem;
import com.garmentfactory.cart.repository.CartItemRepository;
import com.garmentfactory.cart.repository.CartRepository;
import com.garmentfactory.cart.service.CartView.Line;
import com.garmentfactory.cart.service.CartView.LineIssue;
import com.garmentfactory.cart.service.CartView.Totals;
import com.garmentfactory.cart.service.VariantPricer.LineQty;
import com.garmentfactory.cart.service.VariantPricer.PricedVariant;
import com.garmentfactory.inventory.api.InventoryApi;
import com.garmentfactory.inventory.api.StockAvailability;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shipping.api.ShippingQuote;
import com.garmentfactory.shipping.api.ShippingQuoteApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DB-backed cart. Prices, stock, coupon, GST and shipping are recomputed live on every call;
 * nothing price-related is stored on the cart. Stock is only CHECKED here; it is reserved at order time.
 * Custom-design lines (customDesignId) arrive with Phase 8.
 *
 * <p>Concurrency: every write first takes the cart row lock (see {@link #lockOrCreate}), which queues
 * parallel requests of one customer. All writes run at READ COMMITTED on purpose: under MySQL's default
 * REPEATABLE READ a plain SELECT that ran before the lock (or the first read after it) would keep reading
 * an old snapshot, so a request that waited for the lock could not see the line the previous request had
 * just committed and would insert a duplicate (uq_cart_items_line). At READ COMMITTED every statement sees
 * the latest committed data, and no gap locks are taken between different customers' carts.
 */
@Service
public class CartService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final CartRepository carts;
    private final CartItemRepository items;
    private final VariantPricer pricer;
    private final InventoryApi inventory;
    private final CouponEvaluator couponEvaluator;
    private final TaxCalculator tax;
    private final ShippingQuoteApi shipping;
    private final int maxLineQuantity;
    private final int maxLines;

    public CartService(CartRepository carts,
                       CartItemRepository items,
                       VariantPricer pricer,
                       InventoryApi inventory,
                       CouponEvaluator couponEvaluator,
                       TaxCalculator tax,
                       ShippingQuoteApi shipping,
                       @Value("${app.cart.max-line-quantity:1000}") int maxLineQuantity,
                       @Value("${app.cart.max-lines:50}") int maxLines) {
        this.carts = carts;
        this.items = items;
        this.pricer = pricer;
        this.inventory = inventory;
        this.couponEvaluator = couponEvaluator;
        this.tax = tax;
        this.shipping = shipping;
        this.maxLineQuantity = maxLineQuantity;
        this.maxLines = maxLines;
    }

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public CartView getCart(long userId, ShippingDestination destination) {
        Optional<Cart> cart = carts.findByUserId(userId);
        if (cart.isEmpty()) {
            return assemble(null, List.of(), destination);
        }
        return build(cart.get(), destination);
    }

    // ---------------------------------------------------------------- writes (READ COMMITTED, see class comment)

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartView addItem(long userId, String variantPublicId, int quantity, ShippingDestination destination) {
        if (quantity < 1) {
            throw CartException.invalidQuantity();
        }
        long variantId = pricer.resolveVariantId(variantPublicId).orElseThrow(CartException::variantUnavailable);

        Cart cart = lockOrCreate(userId);
        Optional<CartItem> existing = items.findLine(cart.getId(), variantId, null);

        int newQty = existing.map(i -> i.getQuantity() + quantity).orElse(quantity);
        if (newQty > maxLineQuantity) {
            throw CartException.quantityLimit(maxLineQuantity);
        }
        if (existing.isEmpty() && items.countByCartId(cart.getId()) >= maxLines) {
            throw CartException.tooManyLines(maxLines);
        }
        ensureSellableAndInStock(variantId, newQty);

        if (existing.isPresent()) {
            existing.get().setQuantity(newQty);
        } else {
            items.save(new CartItem(cart.getId(), variantId, null, newQty));
        }
        return build(cart, destination);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartView setQuantity(long userId, long itemId, int quantity, ShippingDestination destination) {
        if (quantity < 1) {
            throw CartException.invalidQuantity();
        }
        if (quantity > maxLineQuantity) {
            throw CartException.quantityLimit(maxLineQuantity);
        }
        Cart cart = lockExisting(userId);
        CartItem item = items.findByIdAndCartId(itemId, cart.getId()).orElseThrow(CartException::itemNotFound);
        ensureSellableAndInStock(item.getVariantId(), quantity);
        item.setQuantity(quantity);
        return build(cart, destination);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartView removeItem(long userId, long itemId, ShippingDestination destination) {
        Cart cart = lockExisting(userId);
        CartItem item = items.findByIdAndCartId(itemId, cart.getId()).orElseThrow(CartException::itemNotFound);
        items.delete(item);
        return build(cart, destination);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartView clear(long userId, ShippingDestination destination) {
        Optional<Cart> cart = carts.lockByUserId(userId);
        if (cart.isEmpty()) {
            return assemble(null, List.of(), destination);
        }
        items.deleteAllByCart(cart.get().getId());
        cart.get().setCouponCode(null);
        return assemble(null, List.of(), destination);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartView applyCoupon(long userId, String code, ShippingDestination destination) {
        Cart cart = lockOrCreate(userId);
        List<CartItem> cartItems = items.findByCartIdOrderByIdAsc(cart.getId());
        if (cartItems.isEmpty()) {
            throw CartException.cartEmpty();
        }
        BigDecimal subtotal = priceLines(cartItems).subtotal();
        AppliedCoupon applied = couponEvaluator.evaluate(code, subtotal, Instant.now()); // throws CouponException
        cart.setCouponCode(applied.code());
        return assemble(cart.getCouponCode(), cartItems, destination);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartView removeCoupon(long userId, ShippingDestination destination) {
        Optional<Cart> cart = carts.lockByUserId(userId);
        if (cart.isEmpty()) {
            return assemble(null, List.of(), destination);
        }
        cart.get().setCouponCode(null);
        return build(cart.get(), destination);
    }

    // ---------------------------------------------------------------- checkout (used by CheckoutCartService)

    /** The priced cart plus the variant ids of its lines, in the same order as {@code view.lines()}. */
    public record LockedCart(CartView view, List<Long> variantIds) {
    }

    /** Locks the user's cart row until the caller's transaction ends, then prices it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LockedCart lockForCheckout(long userId, ShippingDestination destination) {
        Cart cart = carts.lockByUserId(userId).orElseThrow(CartException::cartEmpty);
        List<CartItem> cartItems = items.findByCartIdOrderByIdAsc(cart.getId());
        if (cartItems.isEmpty()) {
            throw CartException.cartEmpty();
        }
        CartView view = assemble(cart.getCouponCode(), cartItems, destination);
        return new LockedCart(view, cartItems.stream().map(CartItem::getVariantId).toList());
    }

    /** Empties the cart and forgets its coupon once an order has been created from it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void emptyAfterCheckout(long userId) {
        carts.lockByUserId(userId).ifPresent(cart -> {
            items.deleteAllByCart(cart.getId());
            cart.setCouponCode(null);
        });
    }

    // ---------------------------------------------------------------- internals

    /**
     * Returns the user's cart with its row locked until the transaction ends. A missing cart is created with
     * INSERT ... ON DUPLICATE KEY UPDATE, which takes an exclusive lock on the row whether it is new or
     * already there, so parallel first-adds queue up instead of racing.
     */
    private Cart lockOrCreate(long userId) {
        if (!carts.existsByUserId(userId)) {
            carts.insertIfAbsent(userId);
        }
        return carts.lockByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Cart could not be created for user " + userId));
    }

    private Cart lockExisting(long userId) {
        return carts.lockByUserId(userId).orElseThrow(CartException::itemNotFound);
    }

    private CartView build(Cart cart, ShippingDestination destination) {
        return assemble(cart.getCouponCode(), items.findByCartIdOrderByIdAsc(cart.getId()), destination);
    }

    private void ensureSellableAndInStock(long variantId, int quantity) {
        PricedVariant priced = pricer.priceLines(List.of(new LineQty(variantId, quantity))).get(variantId);
        if (priced == null || !priced.sellable()) {
            throw CartException.variantUnavailable();
        }
        int available = inventory.availability(variantId).map(StockAvailability::available).orElse(0);
        if (available < quantity) {
            throw CartException.insufficientStock(Math.max(available, 0));
        }
    }

    private record PricedLines(List<Line> lines, BigDecimal subtotal, int totalQuantity, int totalWeightGrams,
                               boolean allLinesOk) {
    }

    private PricedLines priceLines(List<CartItem> cartItems) {
        if (cartItems.isEmpty()) {
            return new PricedLines(List.of(), ZERO, 0, 0, false);
        }
        List<LineQty> qtys = cartItems.stream()
                .map(i -> new LineQty(i.getVariantId(), i.getQuantity()))
                .toList();
        Map<Long, PricedVariant> priced = pricer.priceLines(qtys);
        Map<Long, StockAvailability> stock = inventory.availability(
                cartItems.stream().map(CartItem::getVariantId).distinct().toList());

        List<Line> lines = new ArrayList<>();
        BigDecimal subtotal = ZERO;
        int totalQty = 0;
        int weight = 0;
        boolean allOk = true;

        for (CartItem item : cartItems) {
            PricedVariant p = priced.get(item.getVariantId());
            StockAvailability s = stock.get(item.getVariantId());
            int available = s == null ? 0 : Math.max(s.available(), 0);
            int qty = item.getQuantity();

            LineIssue issue = null;
            BigDecimal unit = ZERO;
            BigDecimal lineTotal = ZERO;

            if (p == null || !p.sellable()) {
                issue = LineIssue.UNAVAILABLE;
            } else {
                unit = p.unitPrice();
                lineTotal = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
                subtotal = subtotal.add(lineTotal);
                totalQty += qty;
                weight += p.weightGrams() * qty;
                if (available == 0) {
                    issue = LineIssue.OUT_OF_STOCK;
                } else if (available < qty) {
                    issue = LineIssue.INSUFFICIENT_STOCK;
                }
            }
            if (issue != null) {
                allOk = false;
            }

            lines.add(new Line(item.getId(),
                    p == null ? null : p.variantPublicId(),
                    p == null ? "Unavailable item" : p.productName(),
                    p == null ? null : p.productSlug(),
                    p == null ? null : p.sku(),
                    p == null ? null : p.size(),
                    p == null ? null : p.colorName(),
                    qty, unit, lineTotal,
                    p == null ? ZERO : p.gstRate(), ZERO,
                    available, issue));
        }
        return new PricedLines(lines, subtotal, totalQty, weight, allOk);
    }

    private CartView assemble(String couponCode, List<CartItem> cartItems, ShippingDestination destination) {
        PricedLines pl = priceLines(cartItems);

        AppliedCoupon coupon = null;
        String couponError = null;
        if (couponCode != null && !cartItems.isEmpty()) {
            try {
                coupon = couponEvaluator.evaluate(couponCode, pl.subtotal(), Instant.now());
            } catch (CouponException e) {
                couponError = e.getMessage();
            }
        }
        BigDecimal discount = coupon == null ? ZERO : coupon.discount();

        // GST per line at the product's own rate, on the value after the coupon discount.
        List<Integer> taxedIndexes = new ArrayList<>();
        List<TaxCalculator.TaxableLine> taxLines = new ArrayList<>();
        for (int i = 0; i < pl.lines().size(); i++) {
            Line line = pl.lines().get(i);
            if (line.issue() != LineIssue.UNAVAILABLE) {
                taxedIndexes.add(i);
                taxLines.add(new TaxCalculator.TaxableLine(line.lineTotal(), line.gstRate()));
            }
        }
        TaxCalculator.Result taxResult = tax.compute(taxLines, discount);
        List<Line> finalLines = new ArrayList<>(pl.lines());
        for (int k = 0; k < taxedIndexes.size(); k++) {
            int i = taxedIndexes.get(k);
            finalLines.set(i, finalLines.get(i).withGstAmount(taxResult.gst().get(k)));
        }
        BigDecimal taxable = taxResult.totalTaxable();
        BigDecimal gst = taxResult.totalGst();

        ShippingQuote quote = null;
        String shippingError = null;
        if (destination != null && pl.totalQuantity() > 0) {
            try {
                quote = shipping.quote(destination.state(), destination.pincode(), pl.totalWeightGrams(), taxable);
            } catch (ApiException e) {
                shippingError = e.getMessage();
            }
        }
        BigDecimal shippingCharge = null;
        if (quote != null) {
            shippingCharge = (coupon != null && coupon.freeShipping()) ? ZERO : quote.charge();
        }

        BigDecimal total = taxable.add(gst).add(shippingCharge == null ? ZERO : shippingCharge)
                .setScale(2, RoundingMode.HALF_UP);

        Totals totals = new Totals(pl.totalQuantity(), pl.totalWeightGrams(), pl.subtotal(), discount, taxable,
                gst, shippingCharge, shippingCharge != null, total);

        boolean ready = !cartItems.isEmpty() && pl.allLinesOk();
        return new CartView(finalLines, coupon, couponError, quote, shippingError, totals, ready);
    }
}