package com.garmentfactory.order.service;

/** replayed = true when the Idempotency-Key had already created this order. */
public record PlacedOrder(OrderDetails details, boolean replayed) {
}