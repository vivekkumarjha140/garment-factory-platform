package com.garmentfactory.order.service;

import com.garmentfactory.order.entity.Order;

/** One row of the customer's order history: the order plus what it contains, in two numbers. */
public record OrderSummaryView(Order order, int itemCount, String firstItemName) {
}