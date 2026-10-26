package com.garmentfactory.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Intra-state supply pays CGST + SGST, inter-state pays IGST. */
public final class InvoiceTax {

    public enum Kind { INTRA_STATE, INTER_STATE }

    public record Split(BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {
    }

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal TWO = new BigDecimal("2");

    private InvoiceTax() {
    }

    public static Kind kind(String sellerState, String placeOfSupply) {
        return normalize(sellerState).equals(normalize(placeOfSupply)) ? Kind.INTRA_STATE : Kind.INTER_STATE;
    }

    /** CGST is half rounded down; SGST takes the rest, so the parts always add up to the tax exactly. */
    public static Split split(Kind kind, BigDecimal tax) {
        if (kind == Kind.INTER_STATE) {
            return new Split(ZERO, ZERO, tax);
        }
        BigDecimal cgst = tax.divide(TWO, 2, RoundingMode.DOWN);
        return new Split(cgst, tax.subtract(cgst), ZERO);
    }

    private static String normalize(String state) {
        return state == null ? "" : state.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}