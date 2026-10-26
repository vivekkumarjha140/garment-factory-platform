package com.garmentfactory.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.order.service.InvoiceNumbers;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvoiceNumbersTest {

    @Test
    void theFinancialYearStartsOnFirstAprilInIndia() {
        // 18:29:59 UTC on 31 March is 23:59:59 in India; one second later it is 1 April.
        assertThat(InvoiceNumbers.financialYear(Instant.parse("2026-03-31T18:29:59Z"))).isEqualTo("25-26");
        assertThat(InvoiceNumbers.financialYear(Instant.parse("2026-03-31T18:30:00Z"))).isEqualTo("26-27");
        assertThat(InvoiceNumbers.financialYear(Instant.parse("2027-01-15T10:00:00Z"))).isEqualTo("26-27");
    }

    @Test
    void theNumberStaysWithinTheGstLimitOfSixteenCharacters() {
        String number = InvoiceNumbers.format(1, "26-27");
        assertThat(number).isEqualTo("GF/26-27/000001").hasSizeLessThanOrEqualTo(16);
        assertThat(InvoiceNumbers.format(999999, "26-27")).hasSizeLessThanOrEqualTo(16);
    }

    @Test
    void storageNamesAndSequenceKeysAreSafe() {
        assertThat(InvoiceNumbers.storageName("GF/26-27/000001")).isEqualTo("GF-26-27-000001");
        assertThat(InvoiceNumbers.sequenceKey("26-27")).isEqualTo("INVOICE-26-27");
    }
}