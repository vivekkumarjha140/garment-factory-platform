package com.garmentfactory.catalog.service;

import java.math.BigDecimal;
import java.util.List;

/** Picks the unit price for a quantity. Falls back to the base price when no tier matches. */
public final class TierPricing {

    private TierPricing() {
    }

    public static BigDecimal unitPrice(BigDecimal basePrice, List<PriceTiers.Tier> tiers, int quantity) {
        for (PriceTiers.Tier tier : tiers) {
            boolean aboveMin = quantity >= tier.minQty();
            boolean belowMax = tier.maxQty() == null || quantity <= tier.maxQty();
            if (aboveMin && belowMax) {
                return tier.unitPrice();
            }
        }
        return basePrice;
    }
}