package com.garmentfactory.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.catalog.service.Sizes;
import org.junit.jupiter.api.Test;

class SizesTest {

    @Test
    void acceptsKnownSizesInAnyCaseAndSpacing() {
        assertThat(Sizes.canonical(" xl ")).contains("XL");
        assertThat(Sizes.canonical("3xl")).contains("3XL");
        assertThat(Sizes.canonical("free")).contains("FREE");
        assertThat(Sizes.canonical("4-5y")).contains("4-5Y");
    }

    @Test
    void rejectsUnknownOrMissingSizes() {
        assertThat(Sizes.canonical("Giant")).isEmpty();
        assertThat(Sizes.canonical("")).isEmpty();
        assertThat(Sizes.canonical(null)).isEmpty();
    }
}