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

/** Totals snapshot of a tax invoice. Only the PDF location can change after issue. */
@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, updatable = false, length = 30)
    private String invoiceNumber;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "seller_state", nullable = false, updatable = false, length = 60)
    private String sellerState;

    @Column(name = "place_of_supply", nullable = false, updatable = false, length = 60)
    private String placeOfSupply;

    @Column(name = "gst_type", nullable = false, updatable = false, length = 12)
    private String gstType;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal discountTotal;

    @Column(name = "shipping_total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal shippingTotal;

    @Column(name = "tax_total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal taxTotal;

    @Column(name = "cgst_total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal cgstTotal;

    @Column(name = "sgst_total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal sgstTotal;

    @Column(name = "igst_total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal igstTotal;

    @Column(name = "grand_total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "pdf_storage_key", length = 255)
    private String pdfStorageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}