package com.garmentfactory.order.service;

import com.garmentfactory.order.config.CodProperties;
import com.garmentfactory.order.entity.OrderStatus;
import com.garmentfactory.order.entity.PaymentMethod;
import com.garmentfactory.order.entity.PaymentStatus;
import com.garmentfactory.order.repository.OrderRepository;
import com.garmentfactory.shared.exception.ApiException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CodEligibility {

    private final CodProperties properties;
    private final OrderRepository orders;

    public CodEligibility(CodProperties properties, OrderRepository orders) {
        this.properties = properties;
        this.orders = orders;
    }

    /** Throws 422 with a specific code when this order may not be paid on delivery. */
    public void require(long userId, BigDecimal grandTotal) {
        long open = orders.countByUserIdAndPaymentMethodAndPaymentStatusAndOrderStatusIn(userId, PaymentMethod.COD,
                PaymentStatus.COD_PENDING, List.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING));
        CodRules.evaluate(properties, grandTotal, open).ifPresent(rejection -> {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, rejection.code(), rejection.message());
        });
    }
}