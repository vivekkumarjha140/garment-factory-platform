package com.garmentfactory.payment.service;

import com.garmentfactory.order.api.OrderCancelledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Starts a refund once the cancellation (or the late settlement) that needs it has committed. Never throws. */
@Component
public class RefundTriggers {

    private static final Logger log = LoggerFactory.getLogger(RefundTriggers.class);

    private final RefundService refunds;

    public RefundTriggers(RefundService refunds) {
        this.refunds = refunds;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (event.refundRequired()) {
            run(event.orderId());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onRefundNeeded(RefundNeededEvent event) {
        run(event.orderId());
    }

    private void run(long orderId) {
        try {
            log.info("Refund for order id {}: {}", orderId, refunds.refundOrder(orderId));
        } catch (RuntimeException e) {
            log.error("Refund for order id {} could not be started; the retry sweeper will pick it up", orderId, e);
        }
    }
}