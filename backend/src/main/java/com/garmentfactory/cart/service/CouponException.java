package com.garmentfactory.cart.service;

import com.garmentfactory.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class CouponException extends ApiException {

    private CouponException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static CouponException invalid() {
        return new CouponException("COUPON_INVALID", "This coupon code is not valid.");
    }

    public static CouponException notYetValid() {
        return new CouponException("COUPON_NOT_YET_VALID", "This coupon is not active yet.");
    }

    public static CouponException expired() {
        return new CouponException("COUPON_EXPIRED", "This coupon has expired.");
    }

    public static CouponException exhausted() {
        return new CouponException("COUPON_USAGE_EXHAUSTED", "This coupon has reached its usage limit.");
    }

    public static CouponException minOrderNotMet(BigDecimal minOrderAmount) {
        return new CouponException("COUPON_MIN_ORDER_NOT_MET",
                "Minimum order amount of INR " + minOrderAmount.toPlainString() + " is required for this coupon.");
    }
}