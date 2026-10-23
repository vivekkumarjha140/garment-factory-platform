package com.garmentfactory.customer.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The 28 states and 8 union territories, so addresses and later delivery zones use one spelling. */
public final class IndianStates {

    private static final List<String> NAMES = List.of(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana",
            "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur",
            "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana",
            "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
            "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi",
            "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry");

    private static final Map<String, String> BY_LOWER_CASE = NAMES.stream()
            .collect(Collectors.toUnmodifiableMap(name -> name.toLowerCase(Locale.ROOT), Function.identity()));

    private IndianStates() {
    }

    public static List<String> all() {
        return NAMES;
    }

    /** The official spelling for a state typed in any case, or empty if it is not a known state. */
    public static Optional<String> canonical(String input) {
        if (input == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_LOWER_CASE.get(input.trim().toLowerCase(Locale.ROOT)));
    }
}