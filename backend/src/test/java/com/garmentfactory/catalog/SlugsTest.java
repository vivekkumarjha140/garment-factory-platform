package com.garmentfactory.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.catalog.service.Slugs;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SlugsTest {

    @Test
    void turnsNamesIntoUrlFriendlySlugs() {
        assertThat(Slugs.of("Premium Cotton T-Shirt")).isEqualTo("premium-cotton-t-shirt");
        assertThat(Slugs.of("  Men's   Polo (Navy)!  ")).isEqualTo("men-s-polo-navy");
    }

    @Test
    void removesAccentsAndFallsBackWhenNothingUsableIsLeft() {
        assertThat(Slugs.of("Café Crème")).isEqualTo("cafe-creme");
        assertThat(Slugs.of("!!!")).isEqualTo("item");
        assertThat(Slugs.of(null)).isEqualTo("item");
    }

    @Test
    void longNamesAreCutWithoutATrailingHyphen() {
        String slug = Slugs.of("word ".repeat(40));
        assertThat(slug.length()).isLessThanOrEqualTo(100);
        assertThat(slug).doesNotEndWith("-");
    }

    @Test
    void uniqueAddsTheFirstFreeNumericSuffix() {
        Set<String> taken = Set.of("tee", "tee-2", "tee-3");

        assertThat(Slugs.unique("Tee", taken::contains)).isEqualTo("tee-4");
        assertThat(Slugs.unique("Hoodie", taken::contains)).isEqualTo("hoodie");
    }
}