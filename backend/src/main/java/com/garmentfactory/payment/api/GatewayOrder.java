package com.garmentfactory.payment.api;

public record GatewayOrder(String id, long amountPaise, String currency) {
}