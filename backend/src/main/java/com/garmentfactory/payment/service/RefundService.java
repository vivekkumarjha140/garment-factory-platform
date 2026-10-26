package com.garmentfactory.payment.service;

import com.garmentfactory.order.api.OrderPaymentApi;
import com.garmentfactory.payment.api.GatewayRefund;
import com.garmentfactory.payment.api.PaymentGateway;
import com.garmentfactory.payment.entity.Payment;
import com.garmentfactory.payment.entity.PaymentProvider;
import com.garmentfactory.payment.entity.PaymentRecordStatus;
import com.garmentfactory.payment.entity.PaymentType;
import com.garmentfactory.payment.repository.PaymentRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Full refund of an order's captured online payment. Three steps, so no lock is held during the provider call:
 * 1) under the order lock, record the intent (a REFUND payment row),
 * 2) call the provider,
 * 3) under the lock again, record the outcome. A failed call leaves the row FAILED for the retry sweeper.
 * Repeating it is safe: a finished refund is never asked for twice, and the provider rejects a second full refund.
 */
@Service
public class RefundService {

    public enum Result { REFUNDED, ALREADY_REFUNDED, NOTHING_TO_REFUND, FAILED }

    private record Plan(Result decided, long refundPaymentId, String providerPaymentId, long amountPaise, String receipt) {
        static Plan done(Result result) {
            return new Plan(result, 0, null, 0, null);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final TransactionTemplate tx;
    private final PaymentRepository payments;
    private final OrderPaymentApi orders;
    private final PaymentGateway gateway;

    public RefundService(PlatformTransactionManager txManager, PaymentRepository payments, OrderPaymentApi orders,
                         PaymentGateway gateway) {
        this.tx = new TransactionTemplate(txManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.payments = payments;
        this.orders = orders;
        this.gateway = gateway;
    }

    public Result refundOrder(long orderId) {
        Plan plan = tx.execute(status -> plan(orderId));
        if (plan.decided() != null) {
            return plan.decided();
        }
        GatewayRefund refund;
        try {
            refund = gateway.refundPayment(plan.providerPaymentId(), plan.amountPaise(), plan.receipt());
        } catch (RuntimeException e) {
            log.error("Refund for order id {} failed at the provider; it will be retried", orderId, e);
            tx.executeWithoutResult(status -> markFailed(orderId, plan.refundPaymentId(), e));
            return Result.FAILED;
        }
        tx.executeWithoutResult(status -> markRefunded(orderId, plan.refundPaymentId(), refund));
        return Result.REFUNDED;
    }

    private Plan plan(long orderId) {
        orders.lockOrder(orderId);   // order lock first, always
        Optional<Payment> captured = payments.findFirstByOrderIdAndPaymentTypeAndStatus(
                orderId, PaymentType.FULL, PaymentRecordStatus.CAPTURED);
        if (captured.isEmpty()) {
            return Plan.done(Result.NOTHING_TO_REFUND);
        }
        Payment paid = captured.get();
        Payment refund = payments.findFirstByOrderIdAndPaymentTypeOrderByIdDesc(orderId, PaymentType.REFUND).orElse(null);
        if (refund != null && refund.getStatus() == PaymentRecordStatus.REFUNDED) {
            return Plan.done(Result.ALREADY_REFUNDED);
        }
        if (refund == null) {
            refund = new Payment();
            refund.setOrderId(orderId);
            refund.setPaymentType(PaymentType.REFUND);
            refund.setProvider(PaymentProvider.RAZORPAY);
            refund.setAmount(paid.getAmount());
            refund.setIdempotencyKey("REFUND:" + paid.getPublicId());
        } else {
            refund.setFailureReason(null);
        }
        refund.setStatus(PaymentRecordStatus.CREATED);
        refund = payments.save(refund);
        return new Plan(null, refund.getId(), paid.getProviderPaymentId(), PaymentAmounts.toPaise(paid.getAmount()),
                "refund-" + paid.getId());
    }

    private void markRefunded(long orderId, long refundPaymentId, GatewayRefund refund) {
        orders.lockOrder(orderId);
        Payment row = payments.findById(refundPaymentId).orElseThrow();
        if (row.getStatus() != PaymentRecordStatus.REFUNDED) {
            row.setStatus(PaymentRecordStatus.REFUNDED);
            row.setProviderPaymentId(refund.id());   // for a REFUND row this holds the provider's refund id
            row.setFailureReason(null);
            payments.save(row);
        }
        orders.markRefunded(orderId);
    }

    private void markFailed(long orderId, long refundPaymentId, RuntimeException cause) {
        orders.lockOrder(orderId);
        Payment row = payments.findById(refundPaymentId).orElseThrow();
        if (row.getStatus() == PaymentRecordStatus.REFUNDED) {
            return;   // a parallel attempt already succeeded; never downgrade it
        }
        String reason = "Provider refused or was unreachable: " + cause.getMessage();
        row.setStatus(PaymentRecordStatus.FAILED);
        row.setFailureReason(reason.substring(0, Math.min(reason.length(), 255)));
        payments.save(row);
    }
}