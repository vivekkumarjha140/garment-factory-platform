package com.garmentfactory.order.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/** GF/26-27/000001: running number per Indian financial year (April-March). 15 characters; the GST limit is 16. */
public final class InvoiceNumbers {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private InvoiceNumbers() {
    }

    public static String financialYear(Instant at) {
        ZonedDateTime local = at.atZone(BUSINESS_ZONE);
        int start = local.getMonthValue() >= 4 ? local.getYear() : local.getYear() - 1;
        return String.format(Locale.ROOT, "%02d-%02d", start % 100, (start + 1) % 100);
    }

    public static String sequenceKey(String financialYear) {
        return "INVOICE-" + financialYear;
    }

    public static String format(long sequence, String financialYear) {
        return String.format(Locale.ROOT, "GF/%s/%06d", financialYear, sequence);
    }

    /** Safe for storage keys and file names (no slash). */
    public static String storageName(String invoiceNumber) {
        return invoiceNumber.replace('/', '-');
    }
}