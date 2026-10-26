package com.garmentfactory.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.order.service.InvoiceTax;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InvoiceTaxTest {

    @Test
    void sameStateIsIntraStateIgnoringCaseAndSpaces() {
        assertThat(InvoiceTax.kind("Haryana", "  haryana ")).isEqualTo(InvoiceTax.Kind.INTRA_STATE);
        assertThat(InvoiceTax.kind("Haryana", "Delhi")).isEqualTo(InvoiceTax.Kind.INTER_STATE);
    }

    @Test
    void intraStateSplitsTheTaxEquallyIntoCgstAndSgst() {
        InvoiceTax.Split split = InvoiceTax.split(InvoiceTax.Kind.INTRA_STATE, new BigDecimal("45.00"));
        assertThat(split.cgst()).isEqualByComparingTo("22.50");
        assertThat(split.sgst()).isEqualByComparingTo("22.50");
        assertThat(split.igst()).isEqualByComparingTo("0.00");
    }

    @Test
    void anOddPaisaGoesToSgstSoThePartsAlwaysAddUp() {
        BigDecimal tax = new BigDecimal("0.05");
        InvoiceTax.Split split = InvoiceTax.split(InvoiceTax.Kind.INTRA_STATE, tax);
        assertThat(split.cgst()).isEqualByComparingTo("0.02");
        assertThat(split.sgst()).isEqualByComparingTo("0.03");
        assertThat(split.cgst().add(split.sgst()).add(split.igst())).isEqualByComparingTo(tax);
    }

    @Test
    void interStateIsAllIgst() {
        InvoiceTax.Split split = InvoiceTax.split(InvoiceTax.Kind.INTER_STATE, new BigDecimal("45.00"));
        assertThat(split.igst()).isEqualByComparingTo("45.00");
        assertThat(split.cgst()).isEqualByComparingTo("0.00");
        assertThat(split.sgst()).isEqualByComparingTo("0.00");
    }
}