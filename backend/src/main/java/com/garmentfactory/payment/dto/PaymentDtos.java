package com.garmentfactory.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** Everything the Razorpay Checkout widget needs. keyId is the publishable key. */
    public record InitiatePaymentResponse(String keyId, String razorpayOrderId, long amountPaise, String currency,
                                          String orderNumber, String customerName, String customerEmail,
                                          String customerPhone) {
    }

    /** The browser only says WHICH payment to look at. Its status is fetched from the provider by the server. */
    public record SyncRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{6,64}$") String razorpayPaymentId) {
    }

    public record SyncResponse(String orderNumber, String orderStatus, String paymentStatus, String providerStatus) {
    }
}