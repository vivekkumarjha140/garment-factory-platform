package com.garmentfactory.payment.service;

import com.garmentfactory.order.api.OrderPaymentCollectedEvent;
import com.garmentfactory.payment.entity.Payment;
import com.garmentfactory.payment.entity.PaymentProvider;
import com.garmentfactory.payment.entity.PaymentRecordStatus;
import com.garmentfactory.payment.entity.PaymentType;
import com.garmentfactory.payment.repository.PaymentRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Keeps the payments ledger complete: cash collected on delivery becomes a CAPTURED COD payment. Idempotent. */
@Component
public class CodPaymentRecorder {

    private static final Logger log = LoggerFactory.getLogger(CodPaymentRecorder.class);

    private final PaymentRepository payments;

    public CodPaymentRecorder(PaymentRepository payments) {
        this.payments = payments;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCollected(OrderPaymentCollectedEvent event) {
        Payment payment = new Payment();
        payment.setOrderId(event.orderId());
        payment.setPaymentType(PaymentType.COD);
        payment.setProvider(PaymentProvider.COD);
        payment.setAmount(event.amount());
        payment.setStatus(PaymentRecordStatus.CAPTURED);
        payment.setCapturedAt(Instant.now());
        payment.setIdempotencyKey("COD:" + event.orderPublicId());
        try {
            payments.save(payment);
        } catch (DataIntegrityViolationException e) {
            log.info("COD payment for order id {} was already recorded", event.orderId());   // the unique key made this a no-op
        } catch (RuntimeException e) {
            log.error("Could not record the COD payment for order id {}", event.orderId(), e);
        }
    }
}