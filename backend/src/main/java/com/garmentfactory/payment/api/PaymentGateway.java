package com.garmentfactory.payment.api;

/** The payment provider as the payment module sees it. Implemented in the integration module. */
public interface PaymentGateway {

    /** The publishable key the checkout widget needs. Never the secret. */
    String publicKeyId();

    GatewayOrder createOrder(long amountPaise, String currency, String receipt);

    /** Server-to-server lookup: the only trustworthy answer to "was this payment really captured?". */
    GatewayPayment fetchPayment(String providerPaymentId);

    boolean verifyWebhookSignature(String rawBody, String signature);
}