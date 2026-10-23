
package com.garmentfactory.inventory.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class AuditNumbers {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.BASIC_ISO_DATE;

    private AuditNumbers() {
    }

    public static String next() {
        String date = LocalDate.now().format(DATE_FORMAT);

        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase();

        return "AUD-" + date + "-" + suffix;
    }
}

