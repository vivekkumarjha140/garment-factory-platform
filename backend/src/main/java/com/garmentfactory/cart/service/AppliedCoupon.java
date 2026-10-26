package com.garmentfactory.cart.service;

import com.garmentfactory.cart.entity.DiscountType;

import java.math.BigDecimal;

public record AppliedCoupon(
        String code,
        String description,
        DiscountType type,
        BigDecimal discount,
        boolean freeShipping) {
}