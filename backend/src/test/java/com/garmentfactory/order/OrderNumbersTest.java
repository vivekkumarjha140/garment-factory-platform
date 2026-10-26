package com.garmentfactory.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.order.service.OrderNumbers;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderNumbersTest {

    @Test
    void padsTheSequenceToSixDigits() {
        assertThat(OrderNumbers.format(42, Instant.parse("2026-10-26T10:00:00Z"))).isEqualTo("GF-2026-000042");
    }

    @Test
    void usesTheYearInIndiaNotUtc() {
        // 19:00 UTC on 31 Dec is already 00:30 on 1 Jan in India.
        assertThat(OrderNumbers.format(1, Instant.parse("2026-12-31T19:00:00Z"))).isEqualTo("GF-2027-000001");
    }

    @Test
    void doesNotTruncateLargeNumbers() {
        assertThat(OrderNumbers.format(1234567, Instant.parse("2026-06-01T00:00:00Z"))).isEqualTo("GF-2026-1234567");
    }
}