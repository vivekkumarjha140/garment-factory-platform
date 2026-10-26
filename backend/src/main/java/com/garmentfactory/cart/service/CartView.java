package com.garmentfactory.cart.service;

import com.garmentfactory.shipping.api.ShippingQuote;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fully priced snapshot of a cart at this instant. Never stored; recomputed on every request.
 * couponError / shippingError explain why a coupon or shipping charge is missing (they are not thrown).
 */
public record CartView(
        List<Line> lines,
        AppliedCoupon coupon,
        String couponError,
        ShippingQuote shipping,
        String shippingError,
        Totals totals,
        boolean checkoutReady) {

    public enum LineIssue {
        UNAVAILABLE,
        OUT_OF_STOCK,
        INSUFFICIENT_STOCK
    }

    public record Line(
            long itemId,
            String variantPublicId,
            String productName,
            String productSlug,
            String sku,
            String size,
            String colorName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            BigDecimal gstRate,
            BigDecimal gstAmount,
            int availableStock,
            LineIssue issue) {

        public Line withGstAmount(BigDecimal amount) {
            return new Line(itemId, variantPublicId, productName, productSlug, sku, size, colorName, quantity,
                    unitPrice, lineTotal, gstRate, amount, availableStock, issue);
        }
    }

    /** shippingCharge is null until a destination is known (shippingCalculated=false). */
    public record Totals(
            int totalQuantity,
            int totalWeightGrams,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal taxableAmount,
            BigDecimal gstAmount,
            BigDecimal shippingCharge,
            boolean shippingCalculated,
            BigDecimal total) {
    }
}