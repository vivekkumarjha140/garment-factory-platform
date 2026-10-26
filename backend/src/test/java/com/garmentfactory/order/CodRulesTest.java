package com.garmentfactory.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.order.config.CodProperties;
import com.garmentfactory.order.service.CodRules;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CodRulesTest {

    private static final CodProperties ON = new CodProperties(true, new BigDecimal("2000.00"), 2);

    @Test
    void anOrderWithinTheLimitsIsEligible() {
        assertThat(CodRules.evaluate(ON, new BigDecimal("1050.00"), 0)).isEmpty();
        assertThat(CodRules.evaluate(ON, new BigDecimal("1050.00"), 1)).isEmpty();
    }

    @Test
    void theAmountLimitIsInclusive() {
        assertThat(CodRules.evaluate(ON, new BigDecimal("2000.00"), 0)).isEmpty();
        assertThat(CodRules.evaluate(ON, new BigDecimal("2000.01"), 0))
                .hasValueSatisfying(r -> assertThat(r.code()).isEqualTo("COD_LIMIT_EXCEEDED"));
    }

    @Test
    void tooManyOpenCodOrdersAreRefused() {
        assertThat(CodRules.evaluate(ON, new BigDecimal("500.00"), 2))
                .hasValueSatisfying(r -> assertThat(r.code()).isEqualTo("COD_OPEN_ORDERS_LIMIT"));
    }

    @Test
    void codCanBeSwitchedOff() {
        assertThat(CodRules.evaluate(new CodProperties(false, new BigDecimal("2000.00"), 2), new BigDecimal("500.00"), 0))
                .hasValueSatisfying(r -> assertThat(r.code()).isEqualTo("COD_NOT_AVAILABLE"));
    }

    @Test
    void missingSettingsFallBackToSafeDefaults() {
        CodProperties defaults = new CodProperties(true, null, 0);
        assertThat(defaults.maxOrderAmount()).isEqualByComparingTo("5000.00");
        assertThat(defaults.maxOpenOrders()).isEqualTo(2);
    }
}