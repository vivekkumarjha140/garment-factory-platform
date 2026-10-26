package com.garmentfactory.payment.service;

/** Money was collected for an order that cannot be fulfilled. Refund it once the settlement has committed. */
record RefundNeededEvent(long orderId) {
}