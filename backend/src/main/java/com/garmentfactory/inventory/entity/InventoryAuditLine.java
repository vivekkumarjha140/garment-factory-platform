package com.garmentfactory.inventory.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "inventory_audit_lines")
public class InventoryAuditLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audit_id", nullable = false, updatable = false)
    private Long auditId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, updatable = false, length = 20)
    private StockItemType itemType;

    @Column(name = "variant_id", updatable = false)
    private Long variantId;

    @Column(name = "raw_material_id", updatable = false)
    private Long rawMaterialId;

    @Column(name = "system_quantity", nullable = false, updatable = false, precision = 14, scale = 3)
    private BigDecimal systemQuantity;

    @Column(name = "counted_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal countedQuantity;

    @Column(name = "variance", nullable = false, precision = 14, scale = 3)
    private BigDecimal variance;

    @Column(name = "adjustment_movement_id")
    private Long adjustmentMovementId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryAuditLine() { }

    public InventoryAuditLine(Long auditId, StockItemType itemType, Long variantId, Long rawMaterialId,
                              BigDecimal systemQuantity, BigDecimal countedQuantity) {
        this.auditId = auditId;
        this.itemType = itemType;
        this.variantId = variantId;
        this.rawMaterialId = rawMaterialId;
        this.systemQuantity = systemQuantity;
        this.countedQuantity = countedQuantity;
        this.variance = countedQuantity.subtract(systemQuantity);
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public void linkAdjustment(Long movementId) { this.adjustmentMovementId = movementId; }

    public Long getId() { return id; }
    public Long getAuditId() { return auditId; }
    public StockItemType getItemType() { return itemType; }
    public Long getVariantId() { return variantId; }
    public Long getRawMaterialId() { return rawMaterialId; }
    public BigDecimal getSystemQuantity() { return systemQuantity; }
    public BigDecimal getCountedQuantity() { return countedQuantity; }
    public BigDecimal getVariance() { return variance; }
    public Long getAdjustmentMovementId() { return adjustmentMovementId; }
    public Instant getCreatedAt() { return createdAt; }
}
