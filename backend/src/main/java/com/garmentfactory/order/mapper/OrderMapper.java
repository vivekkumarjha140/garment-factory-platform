package com.garmentfactory.order.mapper;

import com.garmentfactory.order.dto.AdminOrderDtos;
import com.garmentfactory.order.dto.OrderDtos;
import com.garmentfactory.order.dto.OrderDtos.AddressResponse;
import com.garmentfactory.order.dto.OrderDtos.OrderItemResponse;
import com.garmentfactory.order.dto.OrderDtos.OrderResponse;
import com.garmentfactory.order.dto.OrderDtos.StatusChangeResponse;
import com.garmentfactory.order.entity.AddressSnapshot;
import com.garmentfactory.order.entity.Order;
import com.garmentfactory.order.service.AdminOrderView;
import com.garmentfactory.order.service.OrderDetails;
import com.garmentfactory.order.service.OrderSummaryView;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(OrderDetails d) {
        Order o = d.order();
        return new OrderResponse(o.getOrderNumber(), o.getOrderStatus().name(), o.getPaymentStatus().name(),
                o.getProductionStatus().name(), o.getShipmentStatus().name(), o.getPaymentMethod().name(),
                o.getPlacedAt(), o.getPaymentDueAt(), address(o.getShipping()), address(o.getBilling()),
                o.getCouponCode(), o.getSubtotal(), o.getDiscountTotal(), o.getShippingTotal(), o.getTaxTotal(),
                o.getGrandTotal(), o.getAmountPaid(),
                d.items().stream().map(i -> new OrderItemResponse(i.getProductName(), i.getSku(), i.getSize(),
                        i.getColor(), i.getQuantity(), i.getUnitPrice(), i.getDiscountAmount(), i.getTaxRate(),
                        i.getTaxAmount(), i.getLineTotal())).toList(),
                d.history().stream().map(h -> new StatusChangeResponse(h.getDimension().name(), h.getFromStatus(),
                        h.getToStatus(), h.getReason(), h.getCreatedAt())).toList());
    }

    private static AddressResponse address(AddressSnapshot a) {
        return new AddressResponse(a.getRecipientName(), a.getPhone(), a.getLine1(), a.getLine2(), a.getCity(),
                a.getState(), a.getPincode());
    }

    public static OrderDtos.OrderSummaryResponse toSummary(OrderSummaryView view) {
        Order o = view.order();
        return new OrderDtos.OrderSummaryResponse(o.getOrderNumber(), o.getOrderStatus().name(), o.getPaymentStatus().name(),
                o.getShipmentStatus().name(), o.getPaymentMethod().name(), o.getPlacedAt(), o.getGrandTotal(),
                view.itemCount(), view.firstItemName());
    }

    public static AdminOrderDtos.AdminOrderSummaryResponse toAdminSummary(OrderSummaryView view) {
        Order o = view.order();
        return new AdminOrderDtos.AdminOrderSummaryResponse(o.getOrderNumber(), o.getCustomerName(), o.getCustomerEmail(),
                o.getOrderStatus().name(), o.getPaymentStatus().name(), o.getShipmentStatus().name(),
                o.getPaymentMethod().name(), o.getPlacedAt(), o.getGrandTotal(), view.itemCount(), view.firstItemName());
    }

    public static AdminOrderDtos.AdminOrderResponse toAdmin(AdminOrderView view) {
        Order o = view.details().order();
        return new AdminOrderDtos.AdminOrderResponse(toResponse(view.details()),
                new AdminOrderDtos.CustomerResponse(o.getCustomerName(), o.getCustomerEmail(), o.getCustomerPhone()), view.invoiceNumber());
    }
}