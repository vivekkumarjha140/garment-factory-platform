package com.garmentfactory.order.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

/** GF-<year in India>-<6 digit running number>. The counter itself lives in number_sequences. */
public final class OrderNumbers {

    public static final String SEQUENCE_KEY = "ORDER";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private OrderNumbers() {
    }

    public static String format(long sequence, Instant placedAt) {
        return String.format(Locale.ROOT, "GF-%d-%06d", placedAt.atZone(BUSINESS_ZONE).getYear(), sequence);
    }
}