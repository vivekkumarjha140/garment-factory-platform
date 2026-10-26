package com.garmentfactory.order.api;

import java.math.BigDecimal;
import java.time.Instant;

/** What the payment module may ask of an order. All mutating methods join the caller's transaction. */
public interface OrderPaymentApi {

    enum Outcome {
        CONFIRMED,
        ALREADY_PAID,
        /** Money arrived but the order cannot be fulfilled (cancelled, or stock gone). A refund is needed. */
        PAID_ORDER_NOT_FULFILLABLE,
        AMOUNT_MISMATCH
    }

    record PayableOrder(long orderId, String orderPublicId, String orderNumber, BigDecimal grandTotal,
                        BigDecimal amountPaid, String orderStatus, String paymentStatus, Instant paymentDueAt,
                        String customerName, String customerEmail, String customerPhone) {
    }

    /** Read-only view of the user's own order (404 ORDER_NOT_FOUND otherwise). */
    PayableOrder viewForUser(long userId, String orderNumber);

    /** Locks the order and checks it can still be paid online (409 ORDER_NOT_PAYABLE / ORDER_PAYMENT_EXPIRED). */
    PayableOrder lockPayableForUser(long userId, String orderNumber);

    void lockOrder(long orderId);

    /** FAILED -> PENDING when the customer retries. No-op in any other state. */
    void reopenAfterFailure(long orderId);

    /** Idempotent. Marks the order PAID and CONFIRMED and turns its stock reservation into a real deduction. */
    Outcome confirmPayment(long orderId, BigDecimal amount);

    /** PENDING -> FAILED only. A failure never overrides a paid order. */
    void markPaymentFailed(long orderId);
}