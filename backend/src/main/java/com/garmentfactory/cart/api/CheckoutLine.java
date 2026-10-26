package com.garmentfactory.cart.api;

import java.math.BigDecimal;

/** One priced cart line, ready to be frozen into an order item. */
public record CheckoutLine(long variantId, String variantPublicId, String productName, String sku, String size,
                           String colorName, int quantity, BigDecimal unitPrice, BigDecimal discountAmount,
                           BigDecimal gstRate, BigDecimal taxAmount) {
}