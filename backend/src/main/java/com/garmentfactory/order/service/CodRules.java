package com.garmentfactory.order.service;

import com.garmentfactory.order.config.CodProperties;
import java.math.BigDecimal;
import java.util.Optional;

/** The cash-on-delivery eligibility rules as a pure function, so they can be tested without a database. */
public final class CodRules {

    public record Rejection(String code, String message) {
    }

    private CodRules() {
    }

    public static Optional<Rejection> evaluate(CodProperties properties, BigDecimal grandTotal, long openCodOrders) {
        if (!properties.enabled()) {
            return Optional.of(new Rejection("COD_NOT_AVAILABLE", "Cash on delivery is not available right now."));
        }
        if (grandTotal.compareTo(properties.maxOrderAmount()) > 0) {
            return Optional.of(new Rejection("COD_LIMIT_EXCEEDED",
                    "Cash on delivery is available for orders up to Rs. " + properties.maxOrderAmount().toPlainString()
                            + ". Please pay online."));
        }
        if (openCodOrders >= properties.maxOpenOrders()) {
            return Optional.of(new Rejection("COD_OPEN_ORDERS_LIMIT",
                    "You have too many open cash-on-delivery orders. Please wait for them to be delivered or pay online."));
        }
        return Optional.empty();
    }
}