package com.garmentfactory.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Frozen line snapshot. The database also refuses UPDATE and DELETE (see V044). */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private Long variantId;

    @Column(name = "product_name", nullable = false, updatable = false, length = 200)
    private String productName;

    @Column(nullable = false, updatable = false, length = 64)
    private String sku;

    @Column(name = "hsn_code", updatable = false, length = 8)
    private String hsnCode;

    @Column(nullable = false, updatable = false, length = 20)
    private String size;

    @Column(nullable = false, updatable = false, length = 50)
    private String color;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "discount_amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "tax_rate", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "line_total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}