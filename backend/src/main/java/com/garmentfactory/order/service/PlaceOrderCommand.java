package com.garmentfactory.order.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** paymentMethod is "ONLINE" or "COD". */
public record PlaceOrderCommand(String idempotencyKey, String shippingAddressId, String billingAddressId,
                                String paymentMethod) {

    /** Identifies "the same request" for idempotency: reusing a key with different input is an error. */
    public String fingerprint() {
        String canonical = "ship=" + shippingAddressId + "|bill=" + (billingAddressId == null ? "" : billingAddressId)
                + "|pay=" + paymentMethod;
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}