package com.garmentfactory.cart.service;

import com.garmentfactory.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CartException extends ApiException {

    private CartException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static CartException itemNotFound() {
        return new CartException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "This item is not in your cart.");
    }

    public static CartException variantUnavailable() {
        return new CartException(HttpStatus.UNPROCESSABLE_ENTITY, "CART_VARIANT_UNAVAILABLE",
                "This product is no longer available.");
    }

    public static CartException insufficientStock(int available) {
        return new CartException(HttpStatus.CONFLICT, "CART_INSUFFICIENT_STOCK",
                available <= 0 ? "This item is out of stock."
                        : "Only " + available + " unit(s) available.");
    }

    public static CartException invalidQuantity() {
        return new CartException(HttpStatus.BAD_REQUEST, "CART_INVALID_QUANTITY", "Quantity must be at least 1.");
    }

    public static CartException quantityLimit(int max) {
        return new CartException(HttpStatus.UNPROCESSABLE_ENTITY, "CART_QUANTITY_LIMIT",
                "You can add at most " + max + " units of one item. For bulk orders please request a quotation.");
    }

    public static CartException tooManyLines(int max) {
        return new CartException(HttpStatus.UNPROCESSABLE_ENTITY, "CART_TOO_MANY_LINES",
                "Your cart can hold at most " + max + " different items.");
    }

    public static CartException cartEmpty() {
        return new CartException(HttpStatus.UNPROCESSABLE_ENTITY, "CART_EMPTY", "Your cart is empty.");
    }
}