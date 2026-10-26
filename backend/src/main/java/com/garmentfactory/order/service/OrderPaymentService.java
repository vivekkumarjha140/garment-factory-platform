package com.garmentfactory.order.service;

import com.garmentfactory.inventory.api.InventoryApi;
import com.garmentfactory.inventory.api.StockAvailability;
import com.garmentfactory.inventory.api.StockLine;
import com.garmentfactory.order.api.OrderPaymentApi;
import com.garmentfactory.order.entity.Order;
import com.garmentfactory.order.entity.OrderItem;
import com.garmentfactory.order.entity.OrderStatus;
import com.garmentfactory.order.entity.OrderStatusHistory;
import com.garmentfactory.order.entity.PaymentMethod;
import com.garmentfactory.order.entity.PaymentStatus;
import com.garmentfactory.order.entity.StatusDimension;
import com.garmentfactory.order.repository.OrderItemRepository;
import com.garmentfactory.order.repository.OrderRepository;
import com.garmentfactory.order.repository.OrderStatusHistoryRepository;
import com.garmentfactory.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPaymentService implements OrderPaymentApi {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentService.class);

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository history;
    private final InventoryApi inventory;

    public OrderPaymentService(OrderRepository orders, OrderItemRepository items,
                               OrderStatusHistoryRepository history, InventoryApi inventory) {
        this.orders = orders;
        this.items = items;
        this.history = history;
        this.inventory = inventory;
    }

    @Override
    @Transactional(readOnly = true)
    public PayableOrder viewForUser(long userId, String orderNumber) {
        return view(orders.findByOrderNumberAndUserId(orderNumber, userId).orElseThrow(OrderPaymentService::notFound));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PayableOrder lockPayableForUser(long userId, String orderNumber) {
        Order order = orders.lockByOrderNumberAndUserId(orderNumber, userId).orElseThrow(OrderPaymentService::notFound);
        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw ApiException.conflict("ORDER_NOT_ONLINE", "This order is not paid online.");
        }
        if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT || order.getPaymentStatus() == PaymentStatus.PAID) {
            throw ApiException.conflict("ORDER_NOT_PAYABLE", "This order cannot be paid any more.");
        }
        if (order.getPaymentDueAt() != null && Instant.now().isAfter(order.getPaymentDueAt())) {
            throw ApiException.conflict("ORDER_PAYMENT_EXPIRED", "The payment window for this order has ended.");
        }
        return view(order);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockOrder(long orderId) {
        orders.lockById(orderId).orElseThrow(OrderPaymentService::notFound);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reopenAfterFailure(long orderId) {
        Order order = orders.lockById(orderId).orElseThrow(OrderPaymentService::notFound);
        if (order.getPaymentStatus() == PaymentStatus.FAILED) {
            movePayment(order, PaymentStatus.PENDING, "Payment retried");
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome confirmPayment(long orderId, BigDecimal amount) {
        Order order = orders.lockById(orderId).orElseThrow(OrderPaymentService::notFound);
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return Outcome.ALREADY_PAID;
        }
        if (amount.compareTo(order.getGrandTotal().subtract(order.getAmountPaid())) != 0) {
            return Outcome.AMOUNT_MISMATCH;
        }
        movePayment(order, PaymentStatus.PAID, "Payment captured");
        order.setAmountPaid(order.getAmountPaid().add(amount));

        if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
            log.warn("Order {} was paid while {}; refund needed", order.getOrderNumber(), order.getOrderStatus());
            return Outcome.PAID_ORDER_NOT_FULFILLABLE;
        }
        if (!holdStock(order)) {
            log.warn("Order {} was paid but stock is gone; refund needed", order.getOrderNumber());
            moveOrder(order, OrderStatus.CANCELLED, "Paid after the stock hold expired and stock is no longer available. Refund required.");
            return Outcome.PAID_ORDER_NOT_FULFILLABLE;
        }
        moveOrder(order, OrderStatus.CONFIRMED, "Payment confirmed");
        orders.save(order); // <-- Yahan add karein
        return Outcome.CONFIRMED;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markPaymentFailed(long orderId) {
        Order order = orders.lockById(orderId).orElseThrow(OrderPaymentService::notFound);
        if (order.getPaymentStatus() == PaymentStatus.PENDING) {
            movePayment(order, PaymentStatus.FAILED, "Payment failed");
        }
    }

    /** Consumes the active reservation; if it had expired, takes the stock again when it is still available. */
    private boolean holdStock(Order order) {
        if (inventory.consumeForOrder(order.getId(), null) > 0) {
            return true;
        }
        List<OrderItem> lines = items.findByOrderIdOrderByIdAsc(order.getId());
        Map<Long, StockAvailability> stock = inventory.availability(lines.stream().map(OrderItem::getVariantId).distinct().toList());
        // Checked first: a failed reserve would mark the whole transaction rollback-only.
        boolean enough = lines.stream().allMatch(l -> stock.containsKey(l.getVariantId())
                && stock.get(l.getVariantId()).available() >= l.getQuantity());
        if (!enough) {
            return false;
        }
        inventory.reserveForOrder(order.getId(),
                lines.stream().map(l -> new StockLine(l.getVariantId(), l.getQuantity())).toList(), null);
        return inventory.consumeForOrder(order.getId(), null) > 0;
    }

    private void movePayment(Order order, PaymentStatus to, String reason) {
        PaymentStatus from = order.getPaymentStatus();
        OrderTransitions.PAYMENT.require(from, to, "Payment");
        order.setPaymentStatus(to);
        orders.save(order); // <-- Yahan add karein
        history.save(OrderStatusHistory.of(order.getId(), StatusDimension.PAYMENT, from.name(), to.name(), null, reason));
    }
    private void moveOrder(Order order, OrderStatus to, String reason) {
        OrderStatus from = order.getOrderStatus();
        OrderTransitions.ORDER.require(from, to, "Order");
        order.setOrderStatus(to);
        orders.save(order); // <-- Yahan add karein
        history.save(OrderStatusHistory.of(order.getId(), StatusDimension.ORDER, from.name(), to.name(), null, reason));
    }

    private static PayableOrder view(Order o) {
        return new PayableOrder(o.getId(), o.getPublicId(), o.getOrderNumber(), o.getGrandTotal(), o.getAmountPaid(),
                o.getOrderStatus().name(), o.getPaymentStatus().name(), o.getPaymentDueAt(),
                o.getCustomerName(), o.getCustomerEmail(), o.getCustomerPhone());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found.");
    }
}