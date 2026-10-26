package com.garmentfactory.order.service;

import com.garmentfactory.order.entity.Order;
import com.garmentfactory.order.entity.OrderItem;
import com.garmentfactory.order.entity.OrderStatusHistory;
import java.util.List;

public record OrderDetails(Order order, List<OrderItem> items, List<OrderStatusHistory> history) {
}