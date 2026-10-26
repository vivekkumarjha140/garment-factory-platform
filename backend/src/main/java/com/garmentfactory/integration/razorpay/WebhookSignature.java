package com.garmentfactory.integration.razorpay;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Razorpay webhook signature: lowercase hex HMAC-SHA256 of the exact raw body, keyed with the webhook secret. */
public final class WebhookSignature {

    private WebhookSignature() {
    }

    public static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available", e);
        }
    }

    /** Fails closed: no secret, no header or no body means invalid. The comparison takes constant time. */
    public static boolean isValid(String secret, String body, String signatureHeader) {
        if (secret == null || secret.isBlank() || body == null || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        byte[] expected = sign(secret, body).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signatureHeader.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}