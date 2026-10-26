package com.garmentfactory.integration.razorpay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.razorpay.*; picked up by @ConfigurationPropertiesScan. Secrets come from the environment only. */
@ConfigurationProperties(prefix = "app.razorpay")
public record RazorpayProperties(String keyId, String keySecret, String webhookSecret, String baseUrl) {

    public RazorpayProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.razorpay.com/v1";
        }
    }

    public boolean configured() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }

    @Override
    public String toString() {
        return "RazorpayProperties[configured=" + configured() + ", baseUrl=" + baseUrl + "]";
    }
}