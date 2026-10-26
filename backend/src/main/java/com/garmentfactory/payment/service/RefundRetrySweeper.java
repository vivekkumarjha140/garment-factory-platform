package com.garmentfactory.payment.service;

import com.garmentfactory.payment.entity.Payment;
import com.garmentfactory.payment.entity.PaymentRecordStatus;
import com.garmentfactory.payment.entity.PaymentType;
import com.garmentfactory.payment.repository.PaymentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries refunds that were requested or failed more than five minutes ago. A permanently failing refund is retried (and logged) every run. */
@Component
@ConditionalOnProperty(prefix = "payments.refund-retry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RefundRetrySweeper {

    private static final Logger log = LoggerFactory.getLogger(RefundRetrySweeper.class);

    private final PaymentRepository payments;
    private final RefundService refunds;

    public RefundRetrySweeper(PaymentRepository payments, RefundService refunds) {
        this.payments = payments;
        this.refunds = refunds;
    }

    @Scheduled(fixedDelayString = "${payments.refund-retry.interval-ms:300000}",
            initialDelayString = "${payments.refund-retry.initial-delay-ms:120000}")
    public void sweep() {
        try {
            List<Payment> stuck = payments.findByPaymentTypeAndStatusInAndUpdatedAtBefore(PaymentType.REFUND,
                    List.of(PaymentRecordStatus.CREATED, PaymentRecordStatus.FAILED),
                    Instant.now().minus(Duration.ofMinutes(5)));
            for (Payment refund : stuck) {
                try {
                    refunds.refundOrder(refund.getOrderId());
                } catch (RuntimeException e) {
                    log.error("Refund retry for order id {} failed", refund.getOrderId(), e);
                }
            }
        } catch (RuntimeException e) {
            log.error("Refund retry sweep failed; will retry on the next run", e);
        }
    }
}