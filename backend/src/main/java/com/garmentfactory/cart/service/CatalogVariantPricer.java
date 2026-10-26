package com.garmentfactory.cart.service;

import com.garmentfactory.catalog.api.VariantPricing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Adapter: cart's VariantPricer port on top of catalog's public pricing API. */
@Component
public class CatalogVariantPricer implements VariantPricer {

    private final VariantPricing catalog;
    private final int defaultWeightGrams;

    public CatalogVariantPricer(VariantPricing catalog,
                                @Value("${app.cart.default-weight-grams:250}") int defaultWeightGrams) {
        this.catalog = catalog;
        this.defaultWeightGrams = defaultWeightGrams;
    }

    @Override
    public Optional<Long> resolveVariantId(String variantPublicId) {
        return catalog.resolveVariantId(variantPublicId);
    }

    @Override
    public Map<Long, PricedVariant> priceLines(List<LineQty> lines) {
        Map<Long, VariantPricing.PricedVariant> priced = catalog.priceLines(
                lines.stream().map(l -> new VariantPricing.Line(l.variantId(), l.quantity())).toList());

        Map<Long, PricedVariant> result = new LinkedHashMap<>();
        priced.forEach((id, p) -> {
            int weight = p.weightGrams() == null || p.weightGrams() <= 0 ? defaultWeightGrams : p.weightGrams();
            result.put(id, new PricedVariant(p.variantId(), p.variantPublicId(), p.productName(), p.productSlug(),
                    p.sku(), p.size(), p.colorName(), p.unitPrice(), p.gstRate(), weight, p.sellable()));
        });
        return result;
    }
}