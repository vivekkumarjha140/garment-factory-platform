package com.garmentfactory.cart.service;

import com.garmentfactory.cart.service.TaxCalculator.Result;
import com.garmentfactory.cart.service.TaxCalculator.TaxableLine;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaxCalculatorTest {

    private final TaxCalculator tax = new TaxCalculator();

    private static TaxableLine line(String total, String rate) {
        return new TaxableLine(new BigDecimal(total), new BigDecimal(rate));
    }

    @Test
    void noLinesGivesZeros() {
        Result r = tax.compute(List.of(), BigDecimal.ZERO);
        assertThat(r.totalTaxable()).isEqualByComparingTo("0.00");
        assertThat(r.totalGst()).isEqualByComparingTo("0.00");
    }

    @Test
    void singleLineGstIsOnDiscountedValue() {
        Result r = tax.compute(List.of(line("1000.00", "5.00")), new BigDecimal("200.00"));
        assertThat(r.totalTaxable()).isEqualByComparingTo("800.00");
        assertThat(r.totalGst()).isEqualByComparingTo("40.00");
    }

    @Test
    void mixedRatesGetProportionalDiscountShare() {
        // 1000 @5% and 500 @12%, discount 150 -> shares 100 and 50
        Result r = tax.compute(List.of(line("1000.00", "5.00"), line("500.00", "12.00")), new BigDecimal("150.00"));
        assertThat(r.taxable().get(0)).isEqualByComparingTo("900.00");
        assertThat(r.taxable().get(1)).isEqualByComparingTo("450.00");
        assertThat(r.gst().get(0)).isEqualByComparingTo("45.00");
        assertThat(r.gst().get(1)).isEqualByComparingTo("54.00");
        assertThat(r.totalGst()).isEqualByComparingTo("99.00");
    }

    @Test
    void roundingRemainderGoesToLastLineSoDiscountIsFullyAllocated() {
        Result r = tax.compute(
                List.of(line("100.00", "5.00"), line("100.00", "5.00"), line("100.00", "5.00")),
                new BigDecimal("10.00"));
        assertThat(r.totalTaxable()).isEqualByComparingTo("290.00");
    }
}