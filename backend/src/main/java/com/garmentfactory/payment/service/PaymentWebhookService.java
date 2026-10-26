package com.garmentfactory.payment.service;

import com.garmentfactory.payment.api.PaymentGateway;
import com.garmentfactory.shared.exception.ApiException;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Razorpay webhooks. Order of defence: signature (fail closed) -> parse -> ledger insert (the unique key dedupes)
 * -> settle. The ledger row and the settlement share ONE transaction: if settling fails the row rolls back, so
 * the provider's retry is processed again instead of being skipped as a duplicate.
 */
@Service
public class PaymentWebhookService {

    public enum Result { PROCESSED, DUPLICATE }

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);
    private static final Set<String> SETTLING_EVENTS = Set.of("payment.captured", "order.paid", "payment.failed");

    private final PaymentGateway gateway;
    private final PaymentSettlementService settlement;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public PaymentWebhookService(PaymentGateway gateway, PaymentSettlementService settlement, JdbcTemplate jdbc,
                                 PlatformTransactionManager txManager) {
        this.gateway = gateway;
        this.settlement = settlement;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public Result handle(String rawBody, String signature, String eventId) {
        if (!gateway.verifyWebhookSignature(rawBody, signature)) {
            log.warn("Rejected a webhook with an invalid signature");
            throw ApiException.unauthorized("INVALID_WEBHOOK_SIGNATURE", "Invalid webhook signature.");
        }
        if (eventId == null || eventId.isBlank() || eventId.length() > 100) {
            throw ApiException.badRequest("WEBHOOK_EVENT_ID_INVALID", "The webhook event id is missing or invalid.");
        }
        Map<String, Object> json;
        try {
            json = JsonParserFactory.getJsonParser().parseMap(rawBody);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("WEBHOOK_PAYLOAD_INVALID", "The webhook payload is not valid JSON.");
        }
        String type = text(json.get("event"));
        if (type == null) {
            throw ApiException.badRequest("WEBHOOK_PAYLOAD_INVALID", "The webhook payload has no event type.");
        }

        return tx.execute(status -> {
            int inserted = jdbc.update("""
                    INSERT IGNORE INTO payment_events
                        (provider, provider_event_id, event_type, payload, signature_valid, received_at)
                    VALUES ('RAZORPAY', ?, ?, CAST(? AS JSON), 1, UTC_TIMESTAMP(6))
                    """, eventId, type, rawBody);
            if (inserted == 0) {
                return Result.DUPLICATE;
            }
            String problem = process(type, json);
            jdbc.update("""
                    UPDATE payment_events SET processed_at = UTC_TIMESTAMP(6), processing_error = ?
                     WHERE provider = 'RAZORPAY' AND provider_event_id = ?
                    """, problem == null ? null : problem.substring(0, Math.min(problem.length(), 500)), eventId);
            return Result.PROCESSED;
        });
    }

    /** Returns null when handled cleanly, or a note for the ledger when something needs a human. */
    private String process(String type, Map<String, Object> json) {
        if (!SETTLING_EVENTS.contains(type)) {
            return null;   // acknowledged, nothing for us to do
        }
        Map<String, Object> entity = map(map(map(json.get("payload")).get("payment")).get("entity"));
        String providerPaymentId = text(entity.get("id"));
        String providerOrderId = text(entity.get("order_id"));
        if (providerPaymentId == null || providerOrderId == null) {
            return "The payload has no payment entity.";
        }
        if ("payment.failed".equals(type)) {
            var result = settlement.settleFailed(providerOrderId, providerPaymentId, text(entity.get("error_description")));
            return result == PaymentSettlementService.Settlement.UNKNOWN_ORDER
                    ? "No payment exists for provider order " + providerOrderId : null;
        }
        if (!(entity.get("amount") instanceof Number amount)) {
            return "The payload has no amount.";
        }
        var result = settlement.settleCaptured(providerOrderId, providerPaymentId, amount.longValue(), text(entity.get("currency")));
        return switch (result) {
            case UNKNOWN_ORDER -> "No payment exists for provider order " + providerOrderId;
            case AMOUNT_MISMATCH -> "The captured amount does not match the payment; the order was NOT confirmed.";
            case CAPTURED_NEEDS_REFUND -> "Paid, but the order could not be fulfilled; a refund is required.";
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String text(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}