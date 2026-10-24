
package com.garmentfactory.inventory.service;

/** Keeps free text inside the audit_logs column limits. */
final class AuditText {

    private AuditText() {
    }

    static String shorten(String text, int max) {
        if (text == null) {
            return "";
        }

        String trimmed = text.trim();

        return trimmed.length() <= max
                ? trimmed
                : trimmed.substring(0, max - 3) + "...";
    }
}
