package com.garmentfactory.inventory.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_receipts")
public class StockReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @Column(name = "receipt_number", nullable = false, updatable = false, length = 30)
    private String receiptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, updatable = false, length = 20)
    private StockItemType itemType;

    @Column(name = "variant_id", updatable = false)
    private Long variantId;

    @Column(name = "raw_material_id", updatable = false)
    private Long rawMaterialId;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", updatable = false, precision = 12, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "supplier_name", updatable = false, length = 150)
    private String supplierName;

    @Column(name = "supplier_reference", updatable = false, length = 100)
    private String supplierReference;

    @Column(name = "notes", updatable = false, length = 500)
    private String notes;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "received_by_user_id", nullable = false, updatable = false)
    private Long receivedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockReceipt() { }

    private StockReceipt(String receiptNumber, StockItemType itemType, Long variantId, Long rawMaterialId,
                         BigDecimal quantity, BigDecimal unitCost, String supplierName,
                         String supplierReference, String notes, Instant receivedAt, Long receivedByUserId) {
        this.receiptNumber = receiptNumber;
        this.itemType = itemType;
        this.variantId = variantId;
        this.rawMaterialId = rawMaterialId;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.supplierName = supplierName;
        this.supplierReference = supplierReference;
        this.notes = notes;
        this.receivedAt = receivedAt;
        this.receivedByUserId = receivedByUserId;
    }

    public static StockReceipt forFinishedGood(String receiptNumber, Long variantId, int quantity,
                                               BigDecimal unitCost, String supplierName, String supplierReference,
                                               String notes, Instant receivedAt, Long userId) {
        return new StockReceipt(receiptNumber, StockItemType.FINISHED_GOOD, variantId, null,
                BigDecimal.valueOf(quantity), unitCost, supplierName, supplierReference, notes, receivedAt, userId);
    }

    public static StockReceipt forRawMaterial(String receiptNumber, Long rawMaterialId, BigDecimal quantity,
                                              BigDecimal unitCost, String supplierName, String supplierReference,
                                              String notes, Instant receivedAt, Long userId) {
        return new StockReceipt(receiptNumber, StockItemType.RAW_MATERIAL, null, rawMaterialId,
                quantity, unitCost, supplierName, supplierReference, notes, receivedAt, userId);
    }

    @PrePersist
    void onCreate() {
        if (publicId == null) publicId = UUID.randomUUID().toString();
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getReceiptNumber() { return receiptNumber; }
    public StockItemType getItemType() { return itemType; }
    public Long getVariantId() { return variantId; }
    public Long getRawMaterialId() { return rawMaterialId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public String getSupplierName() { return supplierName; }
    public String getSupplierReference() { return supplierReference; }
    public String getNotes() { return notes; }
    public Instant getReceivedAt() { return receivedAt; }
    public Long getReceivedByUserId() { return receivedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
