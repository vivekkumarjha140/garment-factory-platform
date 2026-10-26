package com.garmentfactory.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class PaymentAmounts {

    private PaymentAmounts() {
    }

    /** Rupees (2 decimals) to paise. Fails loudly instead of rounding money. */
    static long toPaise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }
}