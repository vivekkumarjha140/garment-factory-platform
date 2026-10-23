package com.garmentfactory.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.garmentfactory.catalog.service.PriceTiers;
import com.garmentfactory.catalog.service.PriceTiers.Tier;
import com.garmentfactory.shared.exception.ApiException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceTiersTest {

    private static final BigDecimal BASE = new BigDecimal("500.00");

    private static Tier tier(int min, Integer max, String price) {
        return new Tier(min, max, new BigDecimal(price));
    }

    private static void assertInvalid(List<Tier> tiers, String messagePart) {
        assertThatThrownBy(() -> PriceTiers.validate(tiers, BASE)).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getCode()).isEqualTo("INVALID_PRICE_TIERS");
            assertThat(ex.getMessage()).contains(messagePart);
        });
    }

    @Test
    void acceptsAscendingNonOverlappingTiersWithFallingPrices() {
        assertThatCode(() -> PriceTiers.validate(List.of(
                tier(10, 49, "450.00"), tier(50, 99, "400.00"), tier(100, null, "350.00")), BASE)).doesNotThrowAnyException();
    }

    @Test
    void acceptsNoTiersAtAll() {
        assertThatCode(() -> PriceTiers.validate(List.of(), BASE)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAMaximumBelowTheMinimum() {
        assertInvalid(List.of(tier(10, 5, "450.00")), "maximum quantity");
    }

    @Test
    void rejectsATierDearerThanTheBasePrice() {
        assertInvalid(List.of(tier(10, 49, "500.01")), "base price");
    }

    @Test
    void rejectsOverlappingOrUnorderedTiers() {
        assertInvalid(List.of(tier(10, 49, "450.00"), tier(40, 99, "400.00")), "must not overlap");
        assertInvalid(List.of(tier(50, 99, "400.00"), tier(10, 49, "450.00")), "ascending");
    }

    @Test
    void rejectsAnOpenEndedTierThatIsNotLast() {
        assertInvalid(List.of(tier(10, null, "450.00"), tier(50, 99, "400.00")), "open-ended");
    }

    @Test
    void rejectsPricesThatRiseWithQuantity() {
        assertInvalid(List.of(tier(10, 49, "400.00"), tier(50, 99, "450.00")), "must not rise");
    }
}