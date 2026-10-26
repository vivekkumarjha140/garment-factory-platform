package com.garmentfactory.cart.service;

import com.garmentfactory.cart.api.CheckoutCart;
import com.garmentfactory.cart.api.CheckoutCartApi;
import com.garmentfactory.cart.api.CheckoutLine;
import com.garmentfactory.cart.entity.Coupon;
import com.garmentfactory.cart.entity.CouponRedemption;
import com.garmentfactory.cart.entity.RedemptionStatus;
import com.garmentfactory.cart.repository.CouponRedemptionRepository;
import com.garmentfactory.cart.repository.CouponRepository;
import com.garmentfactory.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutCartService implements CheckoutCartApi {

    private final CartService carts;
    private final TaxCalculator tax;
    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;

    public CheckoutCartService(CartService carts, TaxCalculator tax, CouponRepository coupons,
                               CouponRedemptionRepository redemptions) {
        this.carts = carts;
        this.tax = tax;
        this.coupons = coupons;
        this.redemptions = redemptions;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CheckoutCart lockForCheckout(long userId, String state, String pincode) {
        CartService.LockedCart locked = carts.lockForCheckout(userId, new ShippingDestination(state, pincode));
        CartView view = locked.view();

        if (!view.checkoutReady()) {
            throw unprocessable("CHECKOUT_CART_NOT_READY",
                    "Some items in your cart are unavailable or short on stock. Please review your cart.");
        }
        if (view.couponError() != null) {
            throw unprocessable("COUPON_INVALID", view.couponError());
        }
        if (view.shipping() == null) {
            throw unprocessable("DELIVERY_NOT_AVAILABLE",
                    view.shippingError() != null ? view.shippingError() : "Delivery is not available to this address.");
        }
        if (view.coupon() != null) {
            Coupon coupon = coupons.findByCode(view.coupon().code())
                    .orElseThrow(() -> unprocessable("COUPON_INVALID", "This coupon is no longer valid."));
            Integer perUser = coupon.getUsageLimitPerUser();
            if (perUser != null && redemptions.countByCouponIdAndUserIdAndStatus(
                    coupon.getId(), userId, RedemptionStatus.ACTIVE) >= perUser) {
                throw unprocessable("COUPON_USAGE_EXHAUSTED", "You have already used this coupon the maximum number of times.");
            }
        }

        // Same inputs the cart used, so the per-line discount and GST match what the customer saw.
        List<TaxCalculator.TaxableLine> taxLines = view.lines().stream()
                .map(l -> new TaxCalculator.TaxableLine(l.lineTotal(), l.gstRate()))
                .toList();
        TaxCalculator.Result result = tax.compute(taxLines, view.totals().discount());

        List<CheckoutLine> lines = new ArrayList<>();
        for (int i = 0; i < view.lines().size(); i++) {
            CartView.Line l = view.lines().get(i);
            lines.add(new CheckoutLine(locked.variantIds().get(i), l.variantPublicId(), l.productName(), l.sku(),
                    l.size(), l.colorName(), l.quantity(), l.unitPrice(),
                    l.lineTotal().subtract(result.taxable().get(i)), l.gstRate(), result.gst().get(i)));
        }
        return new CheckoutCart(lines, view.coupon() == null ? null : view.coupon().code(),
                view.shipping().zoneCode(), view.totals().shippingCharge());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeCheckout(long userId, long orderId, CheckoutCart cart) {
        if (cart.couponCode() != null) {
            Coupon coupon = coupons.findByCode(cart.couponCode())
                    .orElseThrow(() -> unprocessable("COUPON_INVALID", "This coupon is no longer valid."));
            if (coupons.incrementUsage(coupon.getId()) == 0) {
                throw unprocessable("COUPON_USAGE_EXHAUSTED", "This coupon has been fully redeemed.");
            }
            CouponRedemption redemption = new CouponRedemption();
            redemption.setCouponId(coupon.getId());
            redemption.setUserId(userId);
            redemption.setOrderId(orderId);
            redemption.setDiscountAmount(cart.discountTotal());
            redemptions.save(redemption);
        }
        carts.emptyAfterCheckout(userId);
    }

    @Override
    @Transactional
    public int releaseCouponForOrder(long orderId) {
        return redemptions.findByOrderIdAndStatus(orderId, RedemptionStatus.ACTIVE).map(r -> {
            r.setStatus(RedemptionStatus.RELEASED);
            r.setReleasedAt(Instant.now());
            coupons.decrementUsage(r.getCouponId());
            return 1;
        }).orElse(0);
    }

    private static ApiException unprocessable(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}