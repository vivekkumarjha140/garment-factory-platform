package com.garmentfactory.cart.service;

import com.garmentfactory.cart.entity.Coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure discount math. No DB, no clock. All money is INR scale 2, HALF_UP. */
public final class CouponCalculator {

    public record Result(BigDecimal discount, boolean freeShipping) {
    }

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private CouponCalculator() {
    }

    public static Result calculate(Coupon coupon, BigDecimal subtotal) {
        BigDecimal base = subtotal.max(BigDecimal.ZERO);
        switch (coupon.getDiscountType()) {
            case PERCENTAGE -> {
                BigDecimal d = base.multiply(coupon.getDiscountValue())
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                if (coupon.getMaxDiscount() != null) {
                    d = d.min(coupon.getMaxDiscount());
                }
                return new Result(d.min(base).setScale(2, RoundingMode.HALF_UP), false);
            }
            case FIXED_AMOUNT -> {
                BigDecimal d = coupon.getDiscountValue().min(base);
                return new Result(d.setScale(2, RoundingMode.HALF_UP), false);
            }
            case FREE_SHIPPING -> {
                return new Result(BigDecimal.ZERO.setScale(2), true);
            }
            default -> throw new IllegalStateException("Unknown discount type " + coupon.getDiscountType());
        }
    }
}