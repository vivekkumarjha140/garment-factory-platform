package com.garmentfactory.catalog.service;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TierPricingTest {

    private static final BigDecimal BASE = new BigDecimal("500.00");

    private static final List<PriceTiers.Tier> TIERS = List.of(
            new PriceTiers.Tier(10, 49, new BigDecimal("400.00")),
            new PriceTiers.Tier(50, null, new BigDecimal("350.00")));

    @Test
    void noTiersMeansBasePrice() {
        assertThat(TierPricing.unitPrice(BASE, List.of(), 100)).isEqualByComparingTo("500.00");
    }

    @Test
    void quantityBelowFirstTierUsesBasePrice() {
        assertThat(TierPricing.unitPrice(BASE, TIERS, 9)).isEqualByComparingTo("500.00");
    }

    @Test
    void tierBoundariesAreInclusive() {
        assertThat(TierPricing.unitPrice(BASE, TIERS, 10)).isEqualByComparingTo("400.00");
        assertThat(TierPricing.unitPrice(BASE, TIERS, 49)).isEqualByComparingTo("400.00");
        assertThat(TierPricing.unitPrice(BASE, TIERS, 50)).isEqualByComparingTo("350.00");
    }

    @Test
    void openEndedLastTierCoversLargeQuantities() {
        assertThat(TierPricing.unitPrice(BASE, TIERS, 5000)).isEqualByComparingTo("350.00");
    }
}