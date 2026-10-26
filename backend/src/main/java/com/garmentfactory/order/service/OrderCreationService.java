package com.garmentfactory.order.service;

import com.garmentfactory.catalog.api.VariantTaxInfo;
import com.garmentfactory.cart.api.CheckoutCart;
import com.garmentfactory.cart.api.CheckoutCartApi;
import com.garmentfactory.cart.api.CheckoutLine;
import com.garmentfactory.customer.api.CheckoutAddress;
import com.garmentfactory.customer.api.CheckoutAddressLookup;
import com.garmentfactory.identity.api.UserContacts;
import com.garmentfactory.inventory.api.InventoryApi;
import com.garmentfactory.inventory.api.StockLine;
import com.garmentfactory.order.api.OrderConfirmedEvent;
import com.garmentfactory.order.entity.AddressSnapshot;
import com.garmentfactory.order.entity.Order;
import com.garmentfactory.order.entity.OrderItem;
import com.garmentfactory.order.entity.OrderStatus;
import com.garmentfactory.order.entity.OrderStatusHistory;
import com.garmentfactory.order.entity.PaymentMethod;
import com.garmentfactory.order.entity.PaymentStatus;
import com.garmentfactory.order.entity.ProductionStatus;
import com.garmentfactory.order.entity.ShipmentStatus;
import com.garmentfactory.order.entity.StatusDimension;
import com.garmentfactory.order.repository.OrderItemRepository;
import com.garmentfactory.order.repository.OrderRepository;
import com.garmentfactory.order.repository.OrderStatusHistoryRepository;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.numbering.NumberSequences;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cart -> order, all or nothing, in ONE transaction: lock cart, validate, create the order and its frozen
 * lines, reserve stock, redeem the coupon, empty the cart. Any failure rolls everything back (including
 * the order number), so a half-made order can never exist.
 * Online orders wait for payment (PENDING_PAYMENT). Cash-on-delivery orders are confirmed at once and their
 * stock is deducted straight away.
 * Not itself @Transactional: the idempotency race handling needs to run after the transaction has ended.
 */
@Service
public class OrderCreationService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final TransactionTemplate tx;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderStatusHistoryRepository history;
    private final OrderQueryService queries;
    private final CheckoutCartApi checkoutCart;
    private final CheckoutAddressLookup addresses;
    private final UserContacts contacts;
    private final InventoryApi inventory;
    private final NumberSequences sequences;
    private final VariantTaxInfo variantTax;
    private final CodEligibility codEligibility;
    private final ApplicationEventPublisher events;
    private final long paymentWindowMinutes;

    public OrderCreationService(PlatformTransactionManager txManager, OrderRepository orders,
                                OrderItemRepository orderItems, OrderStatusHistoryRepository history,
                                OrderQueryService queries, CheckoutCartApi checkoutCart,
                                CheckoutAddressLookup addresses, UserContacts contacts, InventoryApi inventory,
                                NumberSequences sequences, VariantTaxInfo variantTax, CodEligibility codEligibility,
                                ApplicationEventPublisher events,
                                @Value("${inventory.reservation.ttl-minutes:30}") long paymentWindowMinutes) {
        this.tx = new TransactionTemplate(txManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.orders = orders;
        this.orderItems = orderItems;
        this.history = history;
        this.queries = queries;
        this.checkoutCart = checkoutCart;
        this.addresses = addresses;
        this.contacts = contacts;
        this.inventory = inventory;
        this.sequences = sequences;
        this.variantTax = variantTax;
        this.codEligibility = codEligibility;
        this.events = events;
        this.paymentWindowMinutes = paymentWindowMinutes;
    }

    public PlacedOrder place(long userId, PlaceOrderCommand command) {
        String hash = command.fingerprint();

        Optional<Order> existing = orders.findByUserIdAndIdempotencyKey(userId, command.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }
        try {
            return tx.execute(status -> create(userId, command, hash));
        } catch (DataIntegrityViolationException | ApiException e) {
            // A parallel request with the same key may have won (it emptied the cart, or took the unique key).
            Optional<Order> winner = orders.findByUserIdAndIdempotencyKey(userId, command.idempotencyKey());
            if (winner.isPresent()) {
                return replay(winner.get(), hash);
            }
            throw e;
        }
    }

    private PlacedOrder replay(Order order, String hash) {
        if (!order.getRequestHash().equals(hash)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different request.");
        }
        return new PlacedOrder(queries.details(order), true);
    }

    private PlacedOrder create(long userId, PlaceOrderCommand command, String hash) {
        PaymentMethod method = PaymentMethod.valueOf(command.paymentMethod());
        boolean cod = method == PaymentMethod.COD;

        CheckoutAddress ship = addresses.findForUser(userId, command.shippingAddressId())
                .orElseThrow(OrderCreationService::addressNotFound);
        CheckoutAddress bill = command.billingAddressId() == null || command.billingAddressId().isBlank()
                ? ship
                : addresses.findForUser(userId, command.billingAddressId()).orElseThrow(OrderCreationService::addressNotFound);
        UserContacts.Contact contact = contacts.get(userId);

        CheckoutCart cart = checkoutCart.lockForCheckout(userId, ship.state(), ship.pincode());

        // Totals come from the lines themselves, so they always add up exactly.
        BigDecimal subtotal = ZERO;
        BigDecimal discount = ZERO;
        BigDecimal taxTotal = ZERO;
        for (CheckoutLine line : cart.lines()) {
            subtotal = subtotal.add(gross(line));
            discount = discount.add(line.discountAmount());
            taxTotal = taxTotal.add(line.taxAmount());
        }
        BigDecimal shipping = cart.shippingCharge().setScale(2, RoundingMode.HALF_UP);
        BigDecimal grandTotal = subtotal.subtract(discount).add(taxTotal).add(shipping);

        if (cod) {
            codEligibility.require(userId, grandTotal);   // cart is locked, so parallel orders cannot beat the limit
        }

        Instant now = Instant.now();
        Order order = new Order();
        order.setOrderNumber(OrderNumbers.format(sequences.next(OrderNumbers.SEQUENCE_KEY), now));
        order.setUserId(userId);
        order.setPaymentMethod(method);
        order.setCustomerName(contact.fullName());
        order.setCustomerEmail(contact.email());
        order.setCustomerPhone(contact.phone());
        order.setShipping(snapshot(ship));
        order.setBilling(snapshot(bill));
        order.setShippingZoneCode(cart.shippingZoneCode());
        order.setCouponCode(cart.couponCode());
        order.setSubtotal(subtotal);
        order.setDiscountTotal(discount);
        order.setShippingTotal(shipping);
        order.setTaxTotal(taxTotal);
        order.setGrandTotal(grandTotal);
        order.setIdempotencyKey(command.idempotencyKey());
        order.setRequestHash(hash);
        order.setPlacedAt(now);
        if (cod) {
            order.setOrderStatus(OrderStatus.CONFIRMED);
            order.setPaymentStatus(PaymentStatus.COD_PENDING);
        } else {
            // Computed before the stock reservation is made, so the order always expires no later than its stock hold.
            order.setPaymentDueAt(now.plus(Duration.ofMinutes(paymentWindowMinutes)));
        }
        order = orders.saveAndFlush(order);

        Map<Long, String> hsnCodes = variantTax.hsnCodes(cart.lines().stream().map(CheckoutLine::variantId).toList());
        List<OrderItem> items = new ArrayList<>();
        List<StockLine> stockLines = new ArrayList<>();
        for (CheckoutLine line : cart.lines()) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setVariantId(line.variantId());
            item.setProductName(line.productName());
            item.setSku(line.sku());
            item.setHsnCode(hsnCodes.get(line.variantId()));
            item.setSize(line.size());
            item.setColor(line.colorName());
            item.setUnitPrice(line.unitPrice());
            item.setQuantity(line.quantity());
            item.setDiscountAmount(line.discountAmount());
            item.setTaxRate(line.gstRate());
            item.setTaxAmount(line.taxAmount());
            item.setLineTotal(gross(line).subtract(line.discountAmount()).add(line.taxAmount()));
            items.add(item);
            stockLines.add(new StockLine(line.variantId(), line.quantity()));
        }
        orderItems.saveAll(items);

        String reason = cod ? "Order placed (cash on delivery)" : "Order placed";
        List<OrderStatusHistory> trail = history.saveAll(List.of(
                row(order, StatusDimension.ORDER, order.getOrderStatus().name(), userId, reason),
                row(order, StatusDimension.PAYMENT, order.getPaymentStatus().name(), userId, reason),
                row(order, StatusDimension.PRODUCTION, ProductionStatus.NOT_REQUIRED.name(), userId, reason),
                row(order, StatusDimension.SHIPMENT, ShipmentStatus.NOT_SHIPPED.name(), userId, reason)));

        inventory.reserveForOrder(order.getId(), stockLines, userId);   // 409 INSUFFICIENT_STOCK rolls everything back
        if (cod) {
            inventory.consumeForOrder(order.getId(), userId);            // nobody pays online, so deduct at once
        }
        checkoutCart.completeCheckout(userId, order.getId(), cart);

        if (cod) {
            events.publishEvent(new OrderConfirmedEvent(order.getId()));
        }
        return new PlacedOrder(new OrderDetails(order, items, trail), false);
    }

    private static BigDecimal gross(CheckoutLine line) {
        return line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())).setScale(2, RoundingMode.HALF_UP);
    }

    private static AddressSnapshot snapshot(CheckoutAddress a) {
        return new AddressSnapshot(a.recipientName(), a.phone(), a.line1(), a.line2(), a.city(), a.state(), a.pincode());
    }

    private static OrderStatusHistory row(Order order, StatusDimension dimension, String to, long userId, String reason) {
        return OrderStatusHistory.of(order.getId(), dimension, null, to, userId, reason);
    }

    private static ApiException addressNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "Address not found.");
    }
}