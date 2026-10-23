package com.garmentfactory.shared.util;

import java.util.Locale;

public final class Emails {

    private Emails() {
    }

    /** Emails are compared case-insensitively and stored trimmed and lower-cased. */
    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}