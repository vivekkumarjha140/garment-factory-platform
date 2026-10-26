package com.garmentfactory.integration.razorpay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookSignatureTest {

    private static final String BODY = "{\"event\":\"payment.captured\"}";

    @Test
    void matchesTheStandardHmacSha256TestVector() {
        assertThat(WebhookSignature.sign("key", "The quick brown fox jumps over the lazy dog"))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void acceptsTheCorrectSignatureInAnyLetterCase() {
        String signature = WebhookSignature.sign("secret", BODY);
        assertThat(WebhookSignature.isValid("secret", BODY, signature)).isTrue();
        assertThat(WebhookSignature.isValid("secret", BODY, signature.toUpperCase())).isTrue();
    }

    @Test
    void rejectsATamperedBodyOrTheWrongSecret() {
        String signature = WebhookSignature.sign("secret", BODY);
        assertThat(WebhookSignature.isValid("secret", BODY + " ", signature)).isFalse();
        assertThat(WebhookSignature.isValid("other", BODY, signature)).isFalse();
    }

    @Test
    void failsClosedWhenTheSecretIsNotConfigured() {
        assertThat(WebhookSignature.isValid("", BODY, "anything")).isFalse();
        assertThat(WebhookSignature.isValid(null, BODY, "anything")).isFalse();
    }

    @Test
    void rejectsAMissingOrBlankHeader() {
        assertThat(WebhookSignature.isValid("secret", BODY, null)).isFalse();
        assertThat(WebhookSignature.isValid("secret", BODY, "  ")).isFalse();
    }
}