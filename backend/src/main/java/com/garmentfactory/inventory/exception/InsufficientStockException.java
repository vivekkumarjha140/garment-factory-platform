package com.garmentfactory.inventory.exception;

/** 409 with its own code, so clients can tell "not enough stock" from other conflicts. */
public class InsufficientStockException extends StockConflictException {

    public InsufficientStockException(String message) {
        super("INSUFFICIENT_STOCK", message);
    }

    public static InsufficientStockException forVariant(Long variantId, int requested, int available) {
        return new InsufficientStockException(
                "Insufficient stock for variant " + variantId + ": requested " + requested + ", available " + available);
    }
}