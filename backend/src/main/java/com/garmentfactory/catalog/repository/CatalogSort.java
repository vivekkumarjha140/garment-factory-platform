package com.garmentfactory.catalog.repository;

import com.garmentfactory.shared.exception.ApiException;
import java.util.Locale;

/** The only sort orders a shopper can ask for. Each maps to a fixed ORDER BY, never to client text. */
public enum CatalogSort {
    NEWEST,
    PRICE_ASC,
    PRICE_DESC,
    NAME,
    RATING;

    /** Accepts "price_asc" and "price-asc"; an empty value means newest first. */
    public static CatalogSort parse(String value) {
        if (value == null || value.isBlank()) {
            return NEWEST;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_SORT", "Sort must be one of newest, price_asc, price_desc, name, rating.");
        }
    }
}