package com.garmentfactory.catalog.entity;

import com.garmentfactory.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The sellable unit: one size and colour of a product, with its own unique SKU. */
@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
public class ProductVariant extends BaseEntity {

    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    /** Never changes after creation: stock movements and order snapshots refer to it. */
    @Column(nullable = false, updatable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 20)
    private String size;

    @Column(name = "color_name", nullable = false, length = 50)
    private String colorName;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(name = "price_override", precision = 12, scale = 2)
    private BigDecimal priceOverride;

    @Column(name = "weight_g")
    private Integer weightG;

    @Column(length = 32)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private VariantStatus status = VariantStatus.ACTIVE;

    @Version
    @Setter(AccessLevel.NONE)
    private long version;

    @PrePersist
    void assignPublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
    }
}