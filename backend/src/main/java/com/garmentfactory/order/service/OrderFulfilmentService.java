package com.garmentfactory.order.service;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.order.api.OrderPaymentCollectedEvent;
import com.garmentfactory.order.api.OrderShipmentEvent;
import com.garmentfactory.order.entity.Order;
import com.garmentfactory.order.entity.OrderStatus;
import com.garmentfactory.order.entity.OrderStatusHistory;
import com.garmentfactory.order.entity.PaymentMethod;
import com.garmentfactory.order.entity.PaymentStatus;
import com.garmentfactory.order.entity.ShipmentStatus;
import com.garmentfactory.order.entity.StatusDimension;
import com.garmentfactory.order.repository.OrderRepository;
import com.garmentfactory.order.repository.OrderStatusHistoryRepository;
import com.garmentfactory.shared.exception.ApiException;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Staff moving an order through the shop floor: processing, packed, shipped, delivered. Each step runs under the
 * order row lock in one transaction, writes its history row with the staff member as actor, and is idempotent:
 * repeating a step that is already done changes nothing.
 */
@Service
public class OrderFulfilmentService {

    private final TransactionTemplate tx;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final ApplicationEventPublisher events;
    private final AuditRecorder audit;
    private final OrderQueryService queries;

    public OrderFulfilmentService(PlatformTransactionManager txManager, OrderRepository orders,
                                  OrderStatusHistoryRepository history, ApplicationEventPublisher events,
                                  AuditRecorder audit, OrderQueryService queries) {
        this.tx = new TransactionTemplate(txManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.orders = orders;
        this.history = history;
        this.events = events;
        this.audit = audit;
        this.queries = queries;
    }

    public OrderDetails startProcessing(long actorId, String orderNumber, String note) {
        return apply(orderNumber, order -> {
            if (order.getOrderStatus() == OrderStatus.PROCESSING) {
                return;
            }
            requireOrderStatus(order, OrderStatus.CONFIRMED);
            moveOrder(order, OrderStatus.PROCESSING, actorId, noteOr(note, "Processing started"));
        });
    }

    public OrderDetails pack(long actorId, String orderNumber, String note) {
        return apply(orderNumber, order -> {
            if (order.getShipmentStatus() == ShipmentStatus.PACKED) {
                return;
            }
            requireOrderStatus(order, OrderStatus.PROCESSING);
            moveShipment(order, ShipmentStatus.PACKED, actorId, noteOr(note, "Packed"));
        });
    }

    public OrderDetails ship(long actorId, String orderNumber, String note) {
        return apply(orderNumber, order -> {
            if (Set.of(ShipmentStatus.SHIPPED, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED).contains(order.getShipmentStatus())) {
                return;
            }
            requireOrderStatus(order, OrderStatus.PROCESSING);
            if (order.getShipmentStatus() != ShipmentStatus.PACKED) {
                throw ApiException.conflict("ORDER_ACTION_NOT_ALLOWED", "Pack the order before shipping it.");
            }
            moveShipment(order, ShipmentStatus.SHIPPED, actorId, noteOr(note, "Handed over for delivery"));
            events.publishEvent(new OrderShipmentEvent(order.getId(), "SHIPPED"));
        });
    }

    /** For cash on delivery, cashCollected must be true: delivering and getting the money are one step. */
    public OrderDetails deliver(long actorId, String orderNumber, boolean cashCollected, String note) {
        return apply(orderNumber, order -> {
            if (order.getShipmentStatus() == ShipmentStatus.DELIVERED) {
                return;
            }
            requireOrderStatus(order, OrderStatus.PROCESSING);
            if (order.getShipmentStatus() != ShipmentStatus.SHIPPED && order.getShipmentStatus() != ShipmentStatus.OUT_FOR_DELIVERY) {
                throw ApiException.conflict("ORDER_ACTION_NOT_ALLOWED", "Ship the order before marking it delivered.");
            }
            boolean collecting = order.getPaymentMethod() == PaymentMethod.COD && order.getPaymentStatus() == PaymentStatus.COD_PENDING;
            if (collecting && !cashCollected) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COD_CASH_REQUIRED",
                        "Confirm that the cash was collected before marking a cash-on-delivery order delivered.");
            }
            moveShipment(order, ShipmentStatus.DELIVERED, actorId, noteOr(note, "Delivered"));
            moveOrder(order, OrderStatus.COMPLETED, actorId, "Delivered");
            if (collecting) {
                PaymentStatus from = order.getPaymentStatus();
                OrderTransitions.PAYMENT.require(from, PaymentStatus.PAID, "Payment");
                order.setPaymentStatus(PaymentStatus.PAID);
                order.setAmountPaid(order.getGrandTotal());
                history.save(OrderStatusHistory.of(order.getId(), StatusDimension.PAYMENT, from.name(),
                        PaymentStatus.PAID.name(), actorId, "Cash collected on delivery"));
                audit.record(AuditEvent.forActor(actorId, "ORDER_COD_COLLECTED", "ORDER", order.getOrderNumber(),
                        "amount=" + order.getGrandTotal().toPlainString()));
                events.publishEvent(new OrderPaymentCollectedEvent(order.getId(), order.getPublicId(), order.getGrandTotal()));
            }
            events.publishEvent(new OrderShipmentEvent(order.getId(), "DELIVERED"));
        });
    }

    private OrderDetails apply(String orderNumber, Consumer<Order> action) {
        Order order = tx.execute(status -> {
            Order locked = orders.lockByOrderNumber(orderNumber)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found."));
            action.accept(locked);
            return orders.save(locked);
        });
        return queries.details(order);
    }

    private static void requireOrderStatus(Order order, OrderStatus expected) {
        if (order.getOrderStatus() != expected) {
            throw ApiException.conflict("ORDER_ACTION_NOT_ALLOWED", "This action is not available while the order is "
                    + order.getOrderStatus().name().replace('_', ' ').toLowerCase() + ".");
        }
    }

    private void moveOrder(Order order, OrderStatus to, long actorId, String reason) {
        OrderStatus from = order.getOrderStatus();
        OrderTransitions.ORDER.require(from, to, "Order");
        order.setOrderStatus(to);
        history.save(OrderStatusHistory.of(order.getId(), StatusDimension.ORDER, from.name(), to.name(), actorId, reason));
    }

    private void moveShipment(Order order, ShipmentStatus to, long actorId, String reason) {
        ShipmentStatus from = order.getShipmentStatus();
        OrderTransitions.SHIPMENT.require(from, to, "Shipment");
        order.setShipmentStatus(to);
        history.save(OrderStatusHistory.of(order.getId(), StatusDimension.SHIPMENT, from.name(), to.name(), actorId, reason));
    }

    private static String noteOr(String note, String fallback) {
        return note == null || note.isBlank() ? fallback : note.trim();
    }
}