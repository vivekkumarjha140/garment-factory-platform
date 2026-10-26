package com.garmentfactory.payment.service;

import com.garmentfactory.order.api.OrderPaymentApi;
import com.garmentfactory.payment.entity.Payment;
import com.garmentfactory.payment.entity.PaymentProvider;
import com.garmentfactory.payment.entity.PaymentRecordStatus;
import com.garmentfactory.payment.repository.PaymentRepository;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place where a provider's "captured" or "failed" becomes a change in our system. It is reached only
 * from a verified webhook or from our own server-side status fetch, never from anything the browser claims.
 * Lock order is always: order row first, then payment row. Everything here is idempotent.
 */
@Service
public class PaymentSettlementService {

    public enum Settlement {
        CAPTURED, ALREADY_SETTLED, CAPTURED_NEEDS_REFUND, AMOUNT_MISMATCH, FAILED_RECORDED, IGNORED, UNKNOWN_ORDER
    }

    private final PaymentRepository payments;
    private final OrderPaymentApi orders;
    private final ApplicationEventPublisher events;

    public PaymentSettlementService(PaymentRepository payments, OrderPaymentApi orders,
                                    ApplicationEventPublisher events) {
        this.payments = payments;
        this.orders = orders;
        this.events = events;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Settlement settleCaptured(String providerOrderId, String providerPaymentId, long amountPaise, String currency) {
        Optional<Long> orderId = payments.findOrderIdByProviderOrder(PaymentProvider.RAZORPAY, providerOrderId);
        if (orderId.isEmpty()) {
            return Settlement.UNKNOWN_ORDER;
        }
        orders.lockOrder(orderId.get());
        Payment payment = payments.lockByProviderOrder(PaymentProvider.RAZORPAY, providerOrderId).orElseThrow();
        if (payment.getStatus() == PaymentRecordStatus.CAPTURED) {
            return Settlement.ALREADY_SETTLED;
        }
        if (!"INR".equals(currency) || PaymentAmounts.toPaise(payment.getAmount()) != amountPaise) {
            return Settlement.AMOUNT_MISMATCH;
        }
        OrderPaymentApi.Outcome outcome = orders.confirmPayment(payment.getOrderId(), payment.getAmount());
        if (outcome == OrderPaymentApi.Outcome.AMOUNT_MISMATCH) {
            return Settlement.AMOUNT_MISMATCH;
        }
        if (outcome == OrderPaymentApi.Outcome.PAID_ORDER_NOT_FULFILLABLE) {
            events.publishEvent(new RefundNeededEvent(payment.getOrderId()));   // runs only after this transaction commits
        }
        payment.setProviderPaymentId(providerPaymentId);
        payment.setStatus(PaymentRecordStatus.CAPTURED);
        payment.setCapturedAt(Instant.now());
        payment.setFailureReason(null);
        payments.save(payment);
        return switch (outcome) {
            case PAID_ORDER_NOT_FULFILLABLE -> Settlement.CAPTURED_NEEDS_REFUND;
            case ALREADY_PAID -> Settlement.ALREADY_SETTLED;
            default -> Settlement.CAPTURED;
        };
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Settlement settleFailed(String providerOrderId, String providerPaymentId, String reason) {
        Optional<Long> orderId = payments.findOrderIdByProviderOrder(PaymentProvider.RAZORPAY, providerOrderId);
        if (orderId.isEmpty()) {
            return Settlement.UNKNOWN_ORDER;
        }
        orders.lockOrder(orderId.get());
        Payment payment = payments.lockByProviderOrder(PaymentProvider.RAZORPAY, providerOrderId).orElseThrow();
        if (payment.getStatus() == PaymentRecordStatus.CAPTURED || payment.getStatus() == PaymentRecordStatus.FAILED) {
            return Settlement.IGNORED;   // a late or repeated failure never undoes a success
        }
        payment.setStatus(PaymentRecordStatus.FAILED);
        payment.setProviderPaymentId(providerPaymentId);
        payment.setFailureReason(reason == null ? null : reason.substring(0, Math.min(reason.length(), 255)));
        payments.save(payment);
        orders.markPaymentFailed(payment.getOrderId());
        return Settlement.FAILED_RECORDED;
    }
}