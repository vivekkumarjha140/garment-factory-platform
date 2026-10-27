package com.garmentfactory.order.service;

/** A full order as staff see it: the order plus its invoice number, if one has been issued. */
public record AdminOrderView(OrderDetails details, String invoiceNumber) {
}