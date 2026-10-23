package com.garmentfactory.catalog.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The garment sizes the catalog accepts, so "xl", "XL " and "Xl" are one size and typos are rejected. */
public final class Sizes {

    private static final List<String> ALLOWED = List.of(
            "XXS", "XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL", "5XL", "FREE",
            "2-3Y", "4-5Y", "6-7Y", "8-9Y", "10-11Y", "12-13Y");

    private Sizes() {
    }

    public static List<String> all() {
        return ALLOWED;
    }

    public static Optional<String> canonical(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT);
        return ALLOWED.contains(normalized) ? Optional.of(normalized) : Optional.empty();
    }
}