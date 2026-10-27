package com.garmentfactory.order.api;

import java.math.BigDecimal;

/** Published inside the transaction in which staff record the cash collected for a cash-on-delivery order. */
public record OrderPaymentCollectedEvent(long orderId, String orderPublicId, BigDecimal amount) {
}