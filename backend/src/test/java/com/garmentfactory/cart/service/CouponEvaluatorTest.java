package com.garmentfactory.cart.service;

import com.garmentfactory.cart.entity.Coupon;
import com.garmentfactory.cart.entity.DiscountType;
import com.garmentfactory.cart.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    private CouponRepository repo;
    private CouponEvaluator evaluator;
    private Coupon coupon;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(CouponRepository.class);
        evaluator = new CouponEvaluator(repo);
        coupon = new Coupon("SAVE10", DiscountType.PERCENTAGE, new BigDecimal("10"),
                Instant.parse("2026-10-01T00:00:00Z"));
        Mockito.when(repo.findByCode("SAVE10")).thenReturn(Optional.of(coupon));
    }

    @Test
    void validCouponIsAppliedAndCodeIsNormalised() {
        AppliedCoupon a = evaluator.evaluate("  save10 ", new BigDecimal("1000"), NOW);
        assertThat(a.code()).isEqualTo("SAVE10");
        assertThat(a.discount()).isEqualByComparingTo("100.00");
    }

    @Test
    void unknownOrBlankCodeIsInvalid() {
        Mockito.when(repo.findByCode("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> evaluator.evaluate("nope", BigDecimal.TEN, NOW))
                .isInstanceOf(CouponException.class);
        assertThatThrownBy(() -> evaluator.evaluate("   ", BigDecimal.TEN, NOW))
                .isInstanceOf(CouponException.class);
    }

    @Test
    void expiredCouponIsRejected() {
        coupon.setValidUntil(Instant.parse("2026-10-20T00:00:00Z"));
        assertThatThrownBy(() -> evaluator.evaluate("SAVE10", new BigDecimal("1000"), NOW))
                .isInstanceOf(CouponException.class)
                .hasFieldOrPropertyWithValue("code", "COUPON_EXPIRED");
    }

    @Test
    void notYetValidCouponIsRejected() {
        coupon.setValidFrom(Instant.parse("2026-11-01T00:00:00Z"));
        assertThatThrownBy(() -> evaluator.evaluate("SAVE10", new BigDecimal("1000"), NOW))
                .hasFieldOrPropertyWithValue("code", "COUPON_NOT_YET_VALID");
    }

    @Test
    void exhaustedCouponIsRejected() {
        coupon.setUsageLimitTotal(5);
        ReflectionTestUtils.setField(coupon, "timesUsed", 5);
        assertThatThrownBy(() -> evaluator.evaluate("SAVE10", new BigDecimal("1000"), NOW))
                .hasFieldOrPropertyWithValue("code", "COUPON_USAGE_EXHAUSTED");
    }

    @Test
    void belowMinimumOrderIsRejected() {
        coupon.setMinOrderAmount(new BigDecimal("500"));
        assertThatThrownBy(() -> evaluator.evaluate("SAVE10", new BigDecimal("499.99"), NOW))
                .hasFieldOrPropertyWithValue("code", "COUPON_MIN_ORDER_NOT_MET");
    }

    @Test
    void inactiveCouponIsRejectedAsInvalid() {
        coupon.setActive(false);
        assertThatThrownBy(() -> evaluator.evaluate("SAVE10", new BigDecimal("1000"), NOW))
                .hasFieldOrPropertyWithValue("code", "COUPON_INVALID");
    }
}