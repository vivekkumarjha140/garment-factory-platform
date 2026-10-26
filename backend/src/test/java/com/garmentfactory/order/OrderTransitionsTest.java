package com.garmentfactory.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.order.entity.OrderStatus;
import com.garmentfactory.order.entity.PaymentStatus;
import com.garmentfactory.order.entity.ShipmentStatus;
import com.garmentfactory.order.service.OrderTransitions;
import org.junit.jupiter.api.Test;

class OrderTransitionsTest {

    @Test
    void orderMovesForwardOnly() {
        assertThat(OrderTransitions.ORDER.canMove(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderTransitions.ORDER.canMove(OrderStatus.CONFIRMED, OrderStatus.PROCESSING)).isTrue();
        assertThat(OrderTransitions.ORDER.canMove(OrderStatus.PENDING_PAYMENT, OrderStatus.PROCESSING)).isFalse();
        assertThat(OrderTransitions.ORDER.canMove(OrderStatus.PROCESSING, OrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    void cancelledAndCompletedOrdersAreTerminal() {
        for (OrderStatus to : OrderStatus.values()) {
            assertThat(OrderTransitions.ORDER.canMove(OrderStatus.CANCELLED, to)).isFalse();
            assertThat(OrderTransitions.ORDER.canMove(OrderStatus.COMPLETED, to)).isFalse();
        }
    }

    @Test
    void aFailedPaymentCanBeRetriedButAPaidOneCannotGoBackToPending() {
        assertThat(OrderTransitions.PAYMENT.canMove(PaymentStatus.FAILED, PaymentStatus.PENDING)).isTrue();
        assertThat(OrderTransitions.PAYMENT.canMove(PaymentStatus.PAID, PaymentStatus.PENDING)).isFalse();
        assertThat(OrderTransitions.PAYMENT.canMove(PaymentStatus.PENDING, PaymentStatus.PAID)).isTrue();
    }

    @Test
    void aRefundedPaymentIsTerminal() {
        for (PaymentStatus to : PaymentStatus.values()) {
            assertThat(OrderTransitions.PAYMENT.canMove(PaymentStatus.REFUNDED, to)).isFalse();
        }
    }

    @Test
    void shipmentCannotSkipStages() {
        assertThat(OrderTransitions.SHIPMENT.canMove(ShipmentStatus.NOT_SHIPPED, ShipmentStatus.DELIVERED)).isFalse();
        assertThat(OrderTransitions.SHIPMENT.canMove(ShipmentStatus.NOT_SHIPPED, ShipmentStatus.PACKED)).isTrue();
        assertThat(OrderTransitions.SHIPMENT.canMove(ShipmentStatus.SHIPPED, ShipmentStatus.DELIVERED)).isTrue();
    }
}