package com.garmentfactory.cart.service;

import com.garmentfactory.cart.entity.Coupon;
import com.garmentfactory.cart.entity.DiscountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CouponCalculatorTest {

    private static Coupon coupon(DiscountType type, String value) {
        return new Coupon("TEST", type, new BigDecimal(value), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void percentageIsRoundedHalfUp() {
        var r = CouponCalculator.calculate(coupon(DiscountType.PERCENTAGE, "10"), new BigDecimal("1234.55"));
        assertThat(r.discount()).isEqualByComparingTo("123.46");
        assertThat(r.freeShipping()).isFalse();
    }

    @Test
    void percentageIsCappedByMaxDiscount() {
        Coupon c = coupon(DiscountType.PERCENTAGE, "50");
        c.setMaxDiscount(new BigDecimal("200.00"));
        assertThat(CouponCalculator.calculate(c, new BigDecimal("1000")).discount()).isEqualByComparingTo("200.00");
    }

    @Test
    void fixedAmountNeverExceedsSubtotal() {
        var r = CouponCalculator.calculate(coupon(DiscountType.FIXED_AMOUNT, "500"), new BigDecimal("300"));
        assertThat(r.discount()).isEqualByComparingTo("300.00");
    }

    @Test
    void freeShippingGivesNoMoneyDiscountButFlag() {
        var r = CouponCalculator.calculate(coupon(DiscountType.FREE_SHIPPING, "0"), new BigDecimal("999"));
        assertThat(r.discount()).isEqualByComparingTo("0.00");
        assertThat(r.freeShipping()).isTrue();
    }
}