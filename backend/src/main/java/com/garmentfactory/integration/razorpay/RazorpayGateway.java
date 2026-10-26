package com.garmentfactory.integration.razorpay;

import com.garmentfactory.payment.api.GatewayOrder;
import com.garmentfactory.payment.api.GatewayPayment;
import com.garmentfactory.payment.api.PaymentGateway;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.payment.api.GatewayRefund;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Razorpay over plain HTTPS with Basic auth (key id + key secret). Secrets are never logged. */
@Component
public class RazorpayGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);

    private final RazorpayProperties props;
    private final RestClient http;

    public RazorpayGateway(RazorpayProperties props) {
        this.props = props;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    @Override
    public String publicKeyId() {
        requireConfigured();
        return props.keyId();
    }

    @Override
    @SuppressWarnings("unchecked")
    public GatewayOrder createOrder(long amountPaise, String currency, String receipt) {
        requireConfigured();
        Map<String, Object> body = call(() -> http.post().uri("/orders")
                .headers(h -> h.setBasicAuth(props.keyId(), props.keySecret()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amountPaise, "currency", currency, "receipt", receipt))
                .retrieve().body(Map.class));
        return new GatewayOrder(text(body, "id"), number(body, "amount"), text(body, "currency"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public GatewayPayment fetchPayment(String providerPaymentId) {
        requireConfigured();
        Map<String, Object> body = call(() -> http.get().uri("/payments/{id}", providerPaymentId)
                .headers(h -> h.setBasicAuth(props.keyId(), props.keySecret()))
                .retrieve().body(Map.class));
        return new GatewayPayment(text(body, "id"), text(body, "order_id"), number(body, "amount"),
                text(body, "currency"), text(body, "status"), text(body, "error_description"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public GatewayRefund refundPayment(String providerPaymentId, long amountPaise, String receipt) {
        requireConfigured();
        Map<String, Object> body = call(() -> http.post().uri("/payments/{id}/refund", providerPaymentId)
                .headers(h -> h.setBasicAuth(props.keyId(), props.keySecret()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amountPaise, "speed", "normal", "receipt", receipt))
                .retrieve().body(Map.class));
        return new GatewayRefund(text(body, "id"), text(body, "status"));
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return WebhookSignature.isValid(props.webhookSecret(), rawBody, signature);
    }

    private void requireConfigured() {
        if (!props.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_GATEWAY_NOT_CONFIGURED",
                    "Online payment is not available right now.");
        }
    }

    private <T> T call(Supplier<T> action) {
        try {
            T result = action.get();
            if (result == null) {
                throw new RestClientException("Empty response");
            }
            return result;
        } catch (RestClientException e) {
            log.warn("Razorpay call failed: {}", e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR",
                    "The payment provider could not be reached. Please try again.");
        }
    }

    private static String text(Map<String, Object> map, String key) {
        return map.get(key) instanceof String s ? s : null;
    }

    private static long number(Map<String, Object> map, String key) {
        return map.get(key) instanceof Number n ? n.longValue() : 0L;
    }
}