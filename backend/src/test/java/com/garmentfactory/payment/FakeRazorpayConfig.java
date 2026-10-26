package com.garmentfactory.payment;

import com.garmentfactory.integration.razorpay.RazorpayGateway;
import com.garmentfactory.integration.razorpay.RazorpayProperties;
import com.garmentfactory.payment.api.GatewayOrder;
import com.garmentfactory.payment.api.GatewayPayment;
import com.garmentfactory.payment.api.GatewayRefund;
import com.garmentfactory.shared.exception.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;

/** Fakes the outbound Razorpay calls; webhook signature verification stays the real code. Use with @Import. */
@TestConfiguration
public class FakeRazorpayConfig {

    public static class FakeGateway extends RazorpayGateway {

        public record Refund(String providerPaymentId, long amountPaise) {
        }

        public final Map<String, GatewayPayment> payments = new ConcurrentHashMap<>();
        public final List<Refund> refunds = new CopyOnWriteArrayList<>();
        public volatile boolean failRefunds;

        public FakeGateway(RazorpayProperties props) {
            super(props);
        }

        @Override
        public GatewayOrder createOrder(long amountPaise, String currency, String receipt) {
            return new GatewayOrder("order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14), amountPaise, currency);
        }

        @Override
        public GatewayPayment fetchPayment(String providerPaymentId) {
            GatewayPayment payment = payments.get(providerPaymentId);
            if (payment == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", "unknown payment");
            }
            return payment;
        }

        @Override
        public GatewayRefund refundPayment(String providerPaymentId, long amountPaise, String receipt) {
            if (failRefunds) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", "refunds are down");
            }
            refunds.add(new Refund(providerPaymentId, amountPaise));
            return new GatewayRefund("rfnd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14), "processed");
        }
    }

    @Bean
    @Primary
    FakeGateway fakeGateway(RazorpayProperties props) {
        return new FakeGateway(props);
    }
}