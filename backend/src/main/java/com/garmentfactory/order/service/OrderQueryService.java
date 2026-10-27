package com.garmentfactory.order.service;

import com.garmentfactory.order.entity.Order;
import com.garmentfactory.order.entity.OrderItem;
import com.garmentfactory.order.repository.OrderItemRepository;
import com.garmentfactory.order.repository.OrderRepository;
import com.garmentfactory.order.repository.OrderStatusHistoryRepository;
import com.garmentfactory.shared.exception.ApiException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    /** Newest first. size is clamped to 1..50 so nobody can ask for the whole table. */
    public Page<OrderSummaryView> listForUser(long userId, int page, int size) {
        return summarize(orders.findByUserId(userId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "placedAt", "id"))));
    }

    /** Adds the item count and first item name to each order of a page, with one query for all of them. */
    public Page<OrderSummaryView> summarize(Page<Order> found) {
        Map<Long, List<OrderItem>> byOrder = items
                .findByOrderIdInOrderByIdAsc(found.getContent().stream().map(Order::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return found.map(order -> {
            List<OrderItem> lines = byOrder.getOrDefault(order.getId(), List.of());
            return new OrderSummaryView(order, lines.stream().mapToInt(OrderItem::getQuantity).sum(),
                    lines.isEmpty() ? null : lines.get(0).getProductName());
        });
    }

    public OrderDetails details(Order order) {
        return new OrderDetails(order, items.findByOrderIdOrderByIdAsc(order.getId()),
                history.findByOrderIdOrderByIdAsc(order.getId()));
    }
}