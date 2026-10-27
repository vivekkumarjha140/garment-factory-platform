package com.garmentfactory.order.service;

import com.garmentfactory.order.api.OrderPaymentCollectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** A cash-on-delivery order is invoiced once its cash has been collected. A missing invoice is rebuilt on download. */
@Component
public class OrderPaymentCollectedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentCollectedListener.class);

    private final InvoiceService invoices;

    public OrderPaymentCollectedListener(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCollected(OrderPaymentCollectedEvent event) {
        try {
            invoices.ensureForOrder(event.orderId());
        } catch (RuntimeException e) {
            log.error("Invoice could not be issued for order id {}; it will be created on first download", event.orderId(), e);
        }
    }
}