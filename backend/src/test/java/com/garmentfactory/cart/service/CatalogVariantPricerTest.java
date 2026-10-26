package com.garmentfactory.cart.service;

import com.garmentfactory.catalog.api.VariantPricing;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;

class CatalogVariantPricerTest {

    private static VariantPricing.PricedVariant catalogVariant(long id, Integer weight) {
        return new VariantPricing.PricedVariant(id, "pub-" + id, "prod-1", "Cotton Tee", "cotton-tee",
                "TEE-" + id, "M", "Black", new BigDecimal("500.00"), new BigDecimal("5.00"), weight, true);
    }

    @Test
    void missingOrZeroWeightGetsTheDefault() {
        VariantPricing catalog = Mockito.mock(VariantPricing.class);
        Mockito.when(catalog.priceLines(anyList())).thenReturn(Map.of(
                1L, catalogVariant(1L, null),
                2L, catalogVariant(2L, 0)));
        CatalogVariantPricer pricer = new CatalogVariantPricer(catalog, 250);

        var result = pricer.priceLines(List.of(new VariantPricer.LineQty(1L, 1), new VariantPricer.LineQty(2L, 1)));

        assertThat(result.get(1L).weightGrams()).isEqualTo(250);
        assertThat(result.get(2L).weightGrams()).isEqualTo(250);
    }

    @Test
    void realWeightAndFieldsAreKept() {
        VariantPricing catalog = Mockito.mock(VariantPricing.class);
        Mockito.when(catalog.priceLines(anyList())).thenReturn(Map.of(3L, catalogVariant(3L, 180)));
        CatalogVariantPricer pricer = new CatalogVariantPricer(catalog, 250);

        var p = pricer.priceLines(List.of(new VariantPricer.LineQty(3L, 2))).get(3L);

        assertThat(p.weightGrams()).isEqualTo(180);
        assertThat(p.variantPublicId()).isEqualTo("pub-3");
        assertThat(p.gstRate()).isEqualByComparingTo("5.00");
        assertThat(p.sellable()).isTrue();
    }
}