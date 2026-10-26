package com.garmentfactory.cart.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** What the cart needs from the catalog. Implemented by CatalogVariantPricer on top of catalog.api. */
public interface VariantPricer {

    record LineQty(long variantId, int quantity) {
    }

    /**
     * @param unitPrice   current price per unit after override / tier rules (INR, scale 2, tax-exclusive)
     * @param gstRate     percent, e.g. 5.00
     * @param weightGrams never zero/null: the implementation substitutes a default
     * @param sellable    false if the variant or its product is not ACTIVE
     */
    record PricedVariant(long variantId, String variantPublicId, String productName, String productSlug,
                         String sku, String size, String colorName, BigDecimal unitPrice, BigDecimal gstRate,
                         int weightGrams, boolean sellable) {
    }

    Optional<Long> resolveVariantId(String variantPublicId);

    /** Variants that do not exist at all are simply absent from the map. */
    Map<Long, PricedVariant> priceLines(List<LineQty> lines);
}