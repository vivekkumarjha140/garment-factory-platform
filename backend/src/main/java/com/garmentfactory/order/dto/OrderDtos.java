package com.garmentfactory.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    /** paymentMethod is ONLINE (default when omitted) or COD. */
    public record PlaceOrderRequest(@NotBlank @Size(max = 36) String shippingAddressId,
                                    @Size(max = 36) String billingAddressId,
                                    @Pattern(regexp = "ONLINE|COD") String paymentMethod) {
    }

    public record CancelOrderRequest(@Size(max = 200) String reason) {
    }

    public record AddressResponse(String recipientName, String phone, String line1, String line2, String city,
                                  String state, String pincode) {
    }

    public record OrderItemResponse(String productName, String sku, String size, String color, int quantity,
                                    BigDecimal unitPrice, BigDecimal discountAmount, BigDecimal taxRate,
                                    BigDecimal taxAmount, BigDecimal lineTotal) {
    }

    public record StatusChangeResponse(String dimension, String fromStatus, String toStatus, String reason,
                                       Instant at) {
    }

    public record OrderResponse(String orderNumber, String orderStatus, String paymentStatus,
                                String productionStatus, String shipmentStatus, String paymentMethod,
                                Instant placedAt, Instant paymentDueAt, AddressResponse shippingAddress,
                                AddressResponse billingAddress, String couponCode, BigDecimal subtotal,
                                BigDecimal discountTotal, BigDecimal shippingTotal, BigDecimal taxTotal,
                                BigDecimal grandTotal, BigDecimal amountPaid, List<OrderItemResponse> items,
                                List<StatusChangeResponse> history) {
    }

    public record OrderSummaryResponse(String orderNumber, String orderStatus, String paymentStatus,
                                       String shipmentStatus, String paymentMethod, Instant placedAt,
                                       BigDecimal grandTotal, int itemCount, String firstItemName) {
    }
}