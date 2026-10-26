package com.garmentfactory.cart.service;

import com.garmentfactory.cart.entity.Coupon;
import com.garmentfactory.cart.repository.CouponRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Validates a coupon code against a subtotal and computes the discount.
 * Per-user usage limit is enforced at order placement (Phase 7, needs redemption table).
 */
@Component
public class CouponEvaluator {

    private final CouponRepository couponRepository;

    public CouponEvaluator(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    public static String normalize(String rawCode) {
        return rawCode == null ? "" : rawCode.trim().toUpperCase();
    }

    public AppliedCoupon evaluate(String rawCode, BigDecimal subtotal, Instant now) {
        String code = normalize(rawCode);
        if (code.isEmpty()) {
            throw CouponException.invalid();
        }
        Coupon coupon = couponRepository.findByCode(code).orElseThrow(CouponException::invalid);

        if (!coupon.isActive()) {
            throw CouponException.invalid();
        }
        if (now.isBefore(coupon.getValidFrom())) {
            throw CouponException.notYetValid();
        }
        if (coupon.getValidUntil() != null && !now.isBefore(coupon.getValidUntil())) {
            throw CouponException.expired();
        }
        if (coupon.getUsageLimitTotal() != null && coupon.getTimesUsed() >= coupon.getUsageLimitTotal()) {
            throw CouponException.exhausted();
        }
        if (subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw CouponException.minOrderNotMet(coupon.getMinOrderAmount());
        }

        CouponCalculator.Result result = CouponCalculator.calculate(coupon, subtotal);
        return new AppliedCoupon(coupon.getCode(), coupon.getDescription(), coupon.getDiscountType(),
                result.discount(), result.freeShipping());
    }
}