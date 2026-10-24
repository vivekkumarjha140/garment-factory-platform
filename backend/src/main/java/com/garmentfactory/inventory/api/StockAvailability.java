package com.garmentfactory.inventory.api;

public record StockAvailability(
        long variantId,
        int onHand,
        int reserved,
        int available
) { }