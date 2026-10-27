package com.garmentfactory.order.service;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.cart.api.CheckoutCartApi;
import com.garmentfactory.inventory.api.InventoryApi;
import com.garmentfactory.order.api.OrderCancelledEvent;
import com.garmentfactory.order.entity.Order;
import com.garmentfactory.order.entity.OrderStatus;
import com.garmentfactory.order.entity.OrderStatusHistory;
import com.garmentfactory.order.entity.PaymentStatus;
import com.garmentfactory.order.entity.ShipmentStatus;
import com.garmentfactory.order.entity.StatusDimension;
import com.garmentfactory.order.repository.OrderRepository;
import com.garmentfactory.order.repository.OrderStatusHistoryRepository;
import com.garmentfactory.shared.exception.ApiException;
import java.time.Instant;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cancels orders: by the customer, by staff, or by the expiry sweeper when an online order was never paid.
 * Everything happens under the order row lock, in one transaction: status, stock, coupon, history, and the event
 * that triggers a refund when money had been collected.
 */
@Service
public class OrderCancellationService {

    private static final Set<OrderStatus> CUSTOMER_CANCELLABLE = Set.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED);
    private static final Set<OrderStatus> STAFF_CANCELLABLE = Set.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED, OrderStatus.PROCESSING);

    private final TransactionTemplate tx;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final InventoryApi inventory;
    private final CheckoutCartApi coupons;
    private final ApplicationEventPublisher events;
    private final OrderQueryService queries;
    private final AuditRecorder audit;

    public OrderCancellationService(PlatformTransactionManager txManager, OrderRepository orders,
                                    OrderStatusHistoryRepository history, InventoryApi inventory,
                                    CheckoutCartApi coupons, ApplicationEventPublisher events,
                                    OrderQueryService queries, AuditRecorder audit) {
        this.tx = new TransactionTemplate(txManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.orders = orders;
        this.history = history;
        this.inventory = inventory;
        this.coupons = coupons;
        this.events = events;
        this.queries = queries;
        this.audit = audit;
    }

    /** 404 for someone else's order, 409 ORDER_NOT_CANCELLABLE once it is being processed or shipped. Idempotent. */
    public OrderDetails cancelByCustomer(long userId, String orderNumber, String reason) {
        Order order = tx.execute(status -> {
            Order locked = orders.lockByOrderNumberAndUserId(orderNumber, userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found."));
            if (locked.getOrderStatus() == OrderStatus.CANCELLED) {
                return locked;   // already cancelled: repeating the request changes nothing
            }
            if (!CUSTOMER_CANCELLABLE.contains(locked.getOrderStatus()) || locked.getShipmentStatus() != ShipmentStatus.NOT_SHIPPED) {
                throw ApiException.conflict("ORDER_NOT_CANCELLABLE",
                        "This order can no longer be cancelled online. Please contact support.");
            }
            String note = reason == null || reason.isBlank() ? "Cancelled by customer" : "Cancelled by customer: " + reason.trim();
            cancel(locked, note, userId);
            return locked;
        });
        return queries.details(order);
    }

    /** Staff may also cancel an order that is being processed, as long as nothing has been packed or shipped. */
    public OrderDetails cancelByStaff(long actorId, String orderNumber, String reason) {
        Order order = tx.execute(status -> {
            Order locked = orders.lockByOrderNumber(orderNumber)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found."));
            if (locked.getOrderStatus() == OrderStatus.CANCELLED) {
                return locked;
            }
            if (!STAFF_CANCELLABLE.contains(locked.getOrderStatus()) || locked.getShipmentStatus() != ShipmentStatus.NOT_SHIPPED) {
                throw ApiException.conflict("ORDER_NOT_CANCELLABLE",
                        "Packed, shipped or completed orders cannot be cancelled here.");
            }
            boolean refund = locked.getPaymentStatus() == PaymentStatus.PAID;
            cancel(locked, "Cancelled by staff: " + reason.trim(), actorId);
            audit.record(AuditEvent.forActor(actorId, "ORDER_CANCELLED_BY_STAFF", "ORDER", locked.getOrderNumber(),
                    "refundRequired=" + refund));
            return locked;
        });
        return queries.details(order);
    }

    /**
     * Cancels an online order whose payment window ended, unless it got paid (or was cancelled) in the meantime.
     * The cutoff makes the re-check under the lock use the same grace period as the sweeper's query.
     */
    public boolean expireIfStillUnpaid(long orderId, Instant cutoff) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            Order order = orders.lockById(orderId).orElse(null);
            if (order == null || order.getOrderStatus() != OrderStatus.PENDING_PAYMENT
                    || order.getPaymentStatus() == PaymentStatus.PAID
                    || order.getPaymentDueAt() == null || !order.getPaymentDueAt().isBefore(cutoff)) {
                return false;
            }
            cancel(order, "Payment window expired", null);
            return true;
        }));
    }

    private void cancel(Order order, String reason, Long actorUserId) {
        OrderStatus from = order.getOrderStatus();
        boolean stockWasDeducted = from != OrderStatus.PENDING_PAYMENT;   // confirmed and processing orders were deducted
        boolean refundRequired = order.getPaymentStatus() == PaymentStatus.PAID;

        OrderTransitions.ORDER.require(from, OrderStatus.CANCELLED, "Order");
        order.setOrderStatus(OrderStatus.CANCELLED);
        orders.save(order);
        history.save(OrderStatusHistory.of(order.getId(), StatusDimension.ORDER, from.name(),
                OrderStatus.CANCELLED.name(), actorUserId, reason));

        String stockNote = "Order " + order.getOrderNumber() + " cancelled";
        if (stockWasDeducted) {
            inventory.restockForOrder(order.getId(), stockNote, actorUserId);
        } else {
            inventory.releaseForOrder(order.getId(), stockNote, actorUserId);
        }
        coupons.releaseCouponForOrder(order.getId());
        events.publishEvent(new OrderCancelledEvent(order.getId(), refundRequired));
    }
}