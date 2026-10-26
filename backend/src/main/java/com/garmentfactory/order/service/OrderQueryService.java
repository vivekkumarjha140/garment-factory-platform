package com.garmentfactory.order.service;

import com.garmentfactory.order.entity.Order;
import com.garmentfactory.order.repository.OrderItemRepository;
import com.garmentfactory.order.repository.OrderRepository;
import com.garmentfactory.order.repository.OrderStatusHistoryRepository;
import com.garmentfactory.shared.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository history;

    public OrderQueryService(OrderRepository orders, OrderItemRepository items, OrderStatusHistoryRepository history) {
        this.orders = orders;
        this.items = items;
        this.history = history;
    }

    public OrderDetails getForUser(long userId, String orderNumber) {
        Order order = orders.findByOrderNumberAndUserId(orderNumber, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found."));
        return details(order);
    }

    public OrderDetails details(Order order) {
        return new OrderDetails(order, items.findByOrderIdOrderByIdAsc(order.getId()),
                history.findByOrderIdOrderByIdAsc(order.getId()));
    }
}