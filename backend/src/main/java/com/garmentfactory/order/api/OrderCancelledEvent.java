package com.garmentfactory.order.api;

/**
 * Published inside the transaction that cancels an order. refundRequired is true when money had been collected.
 * Listen with {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 */
public record OrderCancelledEvent(long orderId, boolean refundRequired) {
}