package com.garmentfactory.order.api;

/**
 * Published inside the transaction that confirms a paid order. Listen with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so nothing runs for a rolled-back confirmation.
 */
public record OrderConfirmedEvent(long orderId) {
}