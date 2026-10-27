package com.garmentfactory.order.service;

import com.garmentfactory.order.entity.OrderStatus;
import com.garmentfactory.order.entity.PaymentMethod;
import com.garmentfactory.order.entity.PaymentStatus;
import com.garmentfactory.order.entity.ShipmentStatus;

/** Any field may be null, meaning "no filter". q matches the order number, customer name or customer email. */
public record AdminOrderFilter(String q, OrderStatus orderStatus, PaymentStatus paymentStatus,
                               ShipmentStatus shipmentStatus, PaymentMethod paymentMethod) {
}