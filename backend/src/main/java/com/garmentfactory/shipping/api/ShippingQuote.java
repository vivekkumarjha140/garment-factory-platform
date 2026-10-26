package com.garmentfactory.shipping.api;

import java.math.BigDecimal;

public record ShippingQuote(
        String zoneCode,
        String zoneName,
        BigDecimal charge,
        boolean freeShippingApplied,
        int estimatedDaysMin,
        int estimatedDaysMax) {
}