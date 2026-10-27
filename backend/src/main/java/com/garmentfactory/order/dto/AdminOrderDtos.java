package com.garmentfactory.order.dto;

import com.garmentfactory.order.dto.OrderDtos.OrderResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class AdminOrderDtos {

    private AdminOrderDtos() {
    }

    public record AdminOrderSummaryResponse(String orderNumber, String customerName, String customerEmail,
                                            String orderStatus, String paymentStatus, String shipmentStatus,
                                            String paymentMethod, Instant placedAt, BigDecimal grandTotal,
                                            int itemCount, String firstItemName) {
    }

    public record CustomerResponse(String name, String email, String phone) {
    }

    public record AdminOrderResponse(OrderResponse order, CustomerResponse customer, String invoiceNumber) {
    }

    /** Optional free-text note kept in the order's history. */
    public record NoteRequest(@Size(max = 200) String note) {
    }

    /** cashCollected must be true for a cash-on-delivery order; it is ignored for orders already paid. */
    public record DeliverRequest(boolean cashCollected, @Size(max = 200) String note) {
    }

    public record StaffCancelRequest(@NotBlank @Size(max = 200) String reason) {
    }
}