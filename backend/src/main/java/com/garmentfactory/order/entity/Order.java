package com.garmentfactory.order.entity;

import com.garmentfactory.shared.persistence.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A customer order. Everything except the four statuses and amount_paid is frozen at creation
 * (updatable = false), so history can never be rewritten through JPA.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order extends BaseEntity {

    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @Column(name = "order_number", nullable = false, updatable = false, length = 30)
    private String orderNumber;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, updatable = false, length = 20)
    private OrderType orderType = OrderType.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 20)
    private OrderStatus orderStatus = OrderStatus.PENDING_PAYMENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "production_status", nullable = false, length = 20)
    private ProductionStatus productionStatus = ProductionStatus.NOT_REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_status", nullable = false, length = 20)
    private ShipmentStatus shipmentStatus = ShipmentStatus.NOT_SHIPPED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false, length = 10)
    private PaymentMethod paymentMethod = PaymentMethod.ONLINE;

    @Column(name = "customer_name", nullable = false, updatable = false, length = 120)
    private String customerName;

    @Column(name = "customer_email", nullable = false, updatable = false, length = 254)
    private String customerEmail;

    @Column(name = "customer_phone", updatable = false, length = 20)
    private String customerPhone;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "recipientName", column = @Column(name = "ship_name", nullable = false, updatable = false, length = 120)),
            @AttributeOverride(name = "phone", column = @Column(name = "ship_phone", nullable = false, updatable = false, length = 20)),
            @AttributeOverride(name = "line1", column = @Column(name = "ship_line1", nullable = false, updatable = false, length = 200)),
            @AttributeOverride(name = "line2", column = @Column(name = "ship_line2", updatable = false, length = 200)),
            @AttributeOverride(name = "city", column = @Column(name = "ship_city", nullable = false, updatable = false, length = 80)),
            @AttributeOverride(name = "state", column = @Column(name = "ship_state", nullable = false, updatable = false, length = 60)),
            @AttributeOverride(name = "pincode", column = @Column(name = "ship_pincode", nullable = false, updatable = false, length = 6))
    })
    private AddressSnapshot shipping;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "recipientName", column = @Column(name = "bill_name", nullable = false, updatable = false, length = 120)),
            @AttributeOverride(name = "phone", column = @Column(name = "bill_phone", nullable = false, updatable = false, length = 20)),
            @AttributeOverride(name = "line1", column = @Column(name = "bill_line1", nullable = false, updatable = false, length = 200)),
            @AttributeOverride(name = "line2", column = @Column(name = "bill_line2", updatable = false, length = 200)),
            @AttributeOverride(name = "city", column = @Column(name = "bill_city", nullable = false, updatable = false, length = 80)),
            @AttributeOverride(name = "state", column = @Column(name = "bill_state", nullable = false, updatable = false, length = 60)),
            @AttributeOverride(name = "pincode", column = @Column(name = "bill_pincode", nullable = false, updatable = false, length = 6))
    })
    private AddressSnapshot billing;

    @Column(name = "shipping_zone_code", updatable = false, length = 30)
    private String shippingZoneCode;

    @Column(name = "coupon_code", updatable = false, length = 40)
    private String couponCode;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal discountTotal;

    @Column(name = "shipping_total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal shippingTotal;

    @Column(name = "tax_total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal taxTotal;

    @Column(name = "grand_total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid = new BigDecimal("0.00");

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "payment_due_at")
    private Instant paymentDueAt;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void assignPublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
    }
}