package com.garmentfactory.payment.api;

/** A payment as the provider reports it. status is the provider's own word (captured, failed, ...). */
public record GatewayPayment(String id, String orderId, long amountPaise, String currency, String status,
                             String errorDescription) {
}