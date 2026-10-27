package com.garmentfactory.order.api;

/** Published inside the transaction that ships or delivers an order. status is "SHIPPED" or "DELIVERED". */
public record OrderShipmentEvent(long orderId, String status) {
}