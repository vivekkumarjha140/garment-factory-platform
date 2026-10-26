package com.garmentfactory.shipping.api;

import java.math.BigDecimal;

/** Public contract of the shipping module. Other modules (cart, checkout) depend only on this. */
public interface ShippingQuoteApi {

    /**
     * @param state            optional state/UT name (used if no PIN rule matches)
     * @param pincode          6-digit Indian PIN
     * @param totalWeightGrams total parcel weight
     * @param orderValue       goods value after discount (for zone free-shipping threshold)
     */
    ShippingQuote quote(String state, String pincode, int totalWeightGrams, BigDecimal orderValue);
}