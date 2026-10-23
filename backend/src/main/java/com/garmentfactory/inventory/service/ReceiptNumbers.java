package com.garmentfactory.inventory.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Temporary receipt-number generator. Can be swapped for the number_sequences service later. */
final class ReceiptNumbers {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private ReceiptNumbers() { }

    static String next() {
        return "RCV-" + LocalDate.now(ZoneOffset.UTC).format(DATE) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
