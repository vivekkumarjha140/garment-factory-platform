package com.garmentfactory.cart.api;

import java.math.BigDecimal;
import java.util.List;

/** A validated, fully priced cart. shippingCharge is already zero when a coupon gives free shipping. */
public record CheckoutCart(List<CheckoutLine> lines, String couponCode, String shippingZoneCode,
                           BigDecimal shippingCharge) {

    public BigDecimal discountTotal() {
        return lines.stream().map(CheckoutLine::discountAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}