package com.garmentfactory.catalog.service;

import com.garmentfactory.shared.exception.ApiException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Rules for quantity price tiers: ascending and non-overlapping ranges, only the last one open-ended,
 * no tier dearer than the base price, and prices that never rise as the quantity grows.
 */
public final class PriceTiers {

    public record Tier(int minQty, Integer maxQty, BigDecimal unitPrice) {
    }

    private PriceTiers() {
    }

    public static void validate(List<Tier> tiers, BigDecimal basePrice) {
        Tier previous = null;
        for (int i = 0; i < tiers.size(); i++) {
            Tier tier = tiers.get(i);
            String label = "Tier " + (i + 1) + ": ";
            if (tier.maxQty() != null && tier.maxQty() < tier.minQty()) {
                throw invalid(label + "the maximum quantity cannot be below the minimum.");
            }
            if (tier.unitPrice().compareTo(basePrice) > 0) {
                throw invalid(label + "the price cannot be higher than the product's base price.");
            }
            if (previous != null) {
                if (previous.maxQty() == null) {
                    throw invalid(label + "only the last tier can be open-ended.");
                }
                if (tier.minQty() <= previous.maxQty()) {
                    throw invalid(label + "tiers must be in ascending order and must not overlap.");
                }
                if (tier.unitPrice().compareTo(previous.unitPrice()) > 0) {
                    throw invalid(label + "the price must not rise as the quantity grows.");
                }
            }
            previous = tier;
        }
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest("INVALID_PRICE_TIERS", message);
    }
}