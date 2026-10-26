package com.garmentfactory.order.service;

import static com.garmentfactory.order.entity.OrderStatus.*;

import com.garmentfactory.order.entity.OrderStatus;
import com.garmentfactory.order.entity.PaymentStatus;
import com.garmentfactory.order.entity.ProductionStatus;
import com.garmentfactory.order.entity.ShipmentStatus;
import com.garmentfactory.shared.statemachine.TransitionRules;

/** Allowed status changes per dimension, declared once. Anything not listed is rejected. */
public final class OrderTransitions {

    private OrderTransitions() {
    }

    public static final TransitionRules<OrderStatus> ORDER = TransitionRules.forType(OrderStatus.class)
            .allow(PENDING_PAYMENT, CONFIRMED, CANCELLED)
            .allow(CONFIRMED, PROCESSING, CANCELLED)
            .allow(PROCESSING, COMPLETED, CANCELLED)
            .build();

    public static final TransitionRules<PaymentStatus> PAYMENT = TransitionRules.forType(PaymentStatus.class)
            .allow(PaymentStatus.PENDING, PaymentStatus.PAID, PaymentStatus.FAILED)
            .allow(PaymentStatus.FAILED, PaymentStatus.PENDING, PaymentStatus.PAID)
            .allow(PaymentStatus.COD_PENDING, PaymentStatus.PAID)
            .allow(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED)
            .allow(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED)
            .build();

    public static final TransitionRules<ShipmentStatus> SHIPMENT = TransitionRules.forType(ShipmentStatus.class)
            .allow(ShipmentStatus.NOT_SHIPPED, ShipmentStatus.PACKED)
            .allow(ShipmentStatus.PACKED, ShipmentStatus.SHIPPED)
            .allow(ShipmentStatus.SHIPPED, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED)
            .allow(ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED)
            .allow(ShipmentStatus.DELIVERED, ShipmentStatus.RETURNED)
            .build();

    public static final TransitionRules<ProductionStatus> PRODUCTION = TransitionRules.forType(ProductionStatus.class)
            .allow(ProductionStatus.PENDING, ProductionStatus.IN_PRODUCTION)
            .allow(ProductionStatus.IN_PRODUCTION, ProductionStatus.COMPLETED)
            .build();
}