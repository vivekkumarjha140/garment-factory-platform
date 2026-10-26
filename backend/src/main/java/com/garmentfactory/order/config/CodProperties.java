package com.garmentfactory.order.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cash-on-delivery limits (app.cod.*). */
@ConfigurationProperties(prefix = "app.cod")
public record CodProperties(boolean enabled, BigDecimal maxOrderAmount, int maxOpenOrders) {

    public CodProperties {
        if (maxOrderAmount == null) {
            maxOrderAmount = new BigDecimal("5000.00");
        }
        if (maxOpenOrders <= 0) {
            maxOpenOrders = 2;
        }
    }
}