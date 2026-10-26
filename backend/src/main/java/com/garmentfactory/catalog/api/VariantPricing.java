package com.garmentfactory.catalog.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The catalog's pricing contract for cart (and later orders). Prices are INR and tax-exclusive. */
public interface VariantPricing {

    record Line(long variantId, int quantity) {
    }

    /**
     * @param unitPrice   after price-override / quantity-tier rules
     * @param gstRate     percent of the product, e.g. 5.00
     * @param weightGrams null when the variant has no weight configured
     * @param sellable    variant and its product are both ACTIVE
     */
    record PricedVariant(long variantId, String variantPublicId, String productPublicId, String productName,
                         String productSlug, String sku, String size, String colorName,
                         BigDecimal unitPrice, BigDecimal gstRate, Integer weightGrams, boolean sellable) {
    }

    Optional<Long> resolveVariantId(String variantPublicId);

    /**
     * Unknown variant ids are absent from the map. Quantity tiers are chosen from the COMBINED quantity
     * of all given lines that belong to the same product (sizes/colours add up).
     */
    Map<Long, PricedVariant> priceLines(List<Line> lines);
}