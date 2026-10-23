package com.garmentfactory.inventory.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "raw_materials")
public class RawMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @Column(name = "code", nullable = false, updatable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private RawMaterialCategory category;

    @Column(name = "uom", nullable = false, length = 20)
    private String uom;

    @Column(name = "on_hand", nullable = false, precision = 14, scale = 3)
    private BigDecimal onHand = BigDecimal.ZERO;

    @Column(name = "reserved", nullable = false, precision = 14, scale = 3)
    private BigDecimal reserved = BigDecimal.ZERO;

    @Column(name = "low_stock_threshold", nullable = false, precision = 14, scale = 3)
    private BigDecimal lowStockThreshold = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RawMaterial() { }

    public RawMaterial(String code, String name, RawMaterialCategory category, String uom,
                       BigDecimal lowStockThreshold, BigDecimal unitCost) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.uom = uom;
        this.lowStockThreshold = lowStockThreshold;
        this.unitCost = unitCost;
    }

    @PrePersist
    void onCreate() {
        if (publicId == null) publicId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public BigDecimal getAvailable() { return onHand.subtract(reserved); }
    public boolean isLowStock() { return getAvailable().compareTo(lowStockThreshold) <= 0; }

    // Master-data setters only (never stock counters)
    public void setName(String name) { this.name = name; }
    public void setCategory(RawMaterialCategory category) { this.category = category; }
    public void setUom(String uom) { this.uom = uom; }
    public void setLowStockThreshold(BigDecimal v) { this.lowStockThreshold = v; }
    public void setUnitCost(BigDecimal v) { this.unitCost = v; }
    public void setActive(boolean active) { this.active = active; }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public RawMaterialCategory getCategory() { return category; }
    public String getUom() { return uom; }
    public BigDecimal getOnHand() { return onHand; }
    public BigDecimal getReserved() { return reserved; }
    public BigDecimal getLowStockThreshold() { return lowStockThreshold; }
    public BigDecimal getUnitCost() { return unitCost; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
