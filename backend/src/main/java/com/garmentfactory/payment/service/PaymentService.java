package com.garmentfactory.payment.service;

import com.garmentfactory.order.api.OrderPaymentApi;
import com.garmentfactory.order.api.OrderPaymentApi.PayableOrder;
import com.garmentfactory.payment.api.GatewayOrder;
import com.garmentfactory.payment.api.GatewayPayment;
import com.garmentfactory.payment.api.PaymentGateway;
import com.garmentfactory.payment.dto.PaymentDtos.InitiatePaymentResponse;
import com.garmentfactory.payment.dto.PaymentDtos.SyncResponse;
import com.garmentfactory.payment.entity.Payment;
import com.garmentfactory.payment.entity.PaymentProvider;
import com.garmentfactory.payment.entity.PaymentRecordStatus;
import com.garmentfactory.payment.entity.PaymentType;
import com.garmentfactory.payment.repository.PaymentRepository;
import com.garmentfactory.shared.exception.ApiException;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final String CURRENCY = "INR";

    private final PaymentRepository payments;
    private final OrderPaymentApi orders;
    private final PaymentGateway gateway;
    private final PaymentSettlementService settlement;

    public PaymentService(PaymentRepository payments, OrderPaymentApi orders, PaymentGateway gateway,
                          PaymentSettlementService settlement) {
        this.payments = payments;
        this.orders = orders;
        this.gateway = gateway;
        this.settlement = settlement;
    }

    /**
     * Starts (or resumes) online payment of the user's own order. Repeatable: the same provider order is reused
     * until the payment is captured, so a double click or a retry never creates a second charge target.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InitiatePaymentResponse initiate(long userId, String orderNumber) {
        PayableOrder order = orders.lockPayableForUser(userId, orderNumber);   // order lock first

        Optional<Payment> latest = payments.findFirstByOrderIdOrderByIdDesc(order.orderId());
        Payment payment;
        if (latest.isPresent() && latest.get().getStatus() != PaymentRecordStatus.CAPTURED) {
            payment = latest.get();
            if (payment.getStatus() == PaymentRecordStatus.FAILED) {
                payment.setStatus(PaymentRecordStatus.CREATED);
                payment.setFailureReason(null);
                orders.reopenAfterFailure(order.orderId());
            }
        } else {
            BigDecimal due = order.grandTotal().subtract(order.amountPaid());
            GatewayOrder created = gateway.createOrder(PaymentAmounts.toPaise(due), CURRENCY, order.orderNumber());
            payment = new Payment();
            payment.setOrderId(order.orderId());
            payment.setPaymentType(PaymentType.FULL);
            payment.setProvider(PaymentProvider.RAZORPAY);
            payment.setProviderOrderId(created.id());
            payment.setAmount(due);
            payment.setIdempotencyKey("FULL:" + order.orderPublicId());
            payment = payments.save(payment);
        }
        return new InitiatePaymentResponse(gateway.publicKeyId(), payment.getProviderOrderId(),
                PaymentAmounts.toPaise(payment.getAmount()), CURRENCY, order.orderNumber(),
                order.customerName(), order.customerEmail(), order.customerPhone());
    }

    /**
     * "Did my payment go through?" The browser supplies only a payment id. We ask the provider ourselves, check the
     * payment belongs to THIS order, and only then settle. Not transactional: the provider call must not hold locks.
     */
    public SyncResponse sync(long userId, String orderNumber, String providerPaymentId) {
        PayableOrder order = orders.viewForUser(userId, orderNumber);
        Payment payment = payments.findFirstByOrderIdOrderByIdDesc(order.orderId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_STARTED", "Payment has not been started for this order."));

        GatewayPayment fetched = gateway.fetchPayment(providerPaymentId);
        if (!payment.getProviderOrderId().equals(fetched.orderId())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_ORDER_MISMATCH",
                    "This payment does not belong to this order.");
        }
        switch (fetched.status()) {
            case "captured" -> settlement.settleCaptured(fetched.orderId(), fetched.id(), fetched.amountPaise(), fetched.currency());
            case "failed" -> settlement.settleFailed(fetched.orderId(), fetched.id(), fetched.errorDescription());
            default -> { }   // created / authorized / refunded: nothing to settle yet
        }
        PayableOrder after = orders.viewForUser(userId, orderNumber);
        return new SyncResponse(after.orderNumber(), after.orderStatus(), after.paymentStatus(), fetched.status());
    }
}