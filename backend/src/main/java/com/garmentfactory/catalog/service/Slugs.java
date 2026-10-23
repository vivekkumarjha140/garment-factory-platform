package com.garmentfactory.catalog.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** URL-friendly names ("Premium Cotton T-Shirt" becomes "premium-cotton-t-shirt"). */
public final class Slugs {

    private static final int MAX_BASE_LENGTH = 100;
    private static final int MAX_ATTEMPTS = 100;

    private Slugs() {
    }

    public static String of(String text) {
        String ascii = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_BASE_LENGTH) {
            slug = slug.substring(0, MAX_BASE_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "item" : slug;
    }

    /** The slug, or slug-2, slug-3 ... until one is not taken. */
    public static String unique(String text, Predicate<String> taken) {
        String base = of(text);
        String candidate = base;
        for (int suffix = 2; taken.test(candidate); suffix++) {
            if (suffix > MAX_ATTEMPTS) {
                throw new IllegalStateException("Could not find a free slug for " + base);
            }
            candidate = base + "-" + suffix;
        }
        return candidate;
    }
}