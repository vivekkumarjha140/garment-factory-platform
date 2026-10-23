package com.garmentfactory.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.garmentfactory.catalog.repository.CatalogSort;
import com.garmentfactory.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

class CatalogSortTest {

    @Test
    void defaultsToNewestWhenNothingIsGiven() {
        assertThat(CatalogSort.parse(null)).isEqualTo(CatalogSort.NEWEST);
        assertThat(CatalogSort.parse("  ")).isEqualTo(CatalogSort.NEWEST);
    }

    @Test
    void acceptsEverySortInAnyCaseWithUnderscoreOrHyphen() {
        assertThat(CatalogSort.parse("price_asc")).isEqualTo(CatalogSort.PRICE_ASC);
        assertThat(CatalogSort.parse("PRICE-DESC")).isEqualTo(CatalogSort.PRICE_DESC);
        assertThat(CatalogSort.parse("name")).isEqualTo(CatalogSort.NAME);
        assertThat(CatalogSort.parse("Rating")).isEqualTo(CatalogSort.RATING);
    }

    @Test
    void rejectsAnythingElse() {
        assertThatThrownBy(() -> CatalogSort.parse("name; drop table products"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_SORT"));
    }
}