package com.garmentfactory.cart.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Note: DB column line_key is a STORED generated column (variant_id:custom_design_id)
 * used only for the unique index; it is intentionally NOT mapped here.
 */
@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false, updatable = false)
    private Long cartId;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private Long variantId;

    @Column(name = "custom_design_id", updatable = false)
    private Long customDesignId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartItem() {
    }

    public CartItem(Long cartId, Long variantId, Long customDesignId, int quantity) {
        this.cartId = cartId;
        this.variantId = variantId;
        this.customDesignId = customDesignId;
        this.quantity = quantity;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getCartId() { return cartId; }
    public Long getVariantId() { return variantId; }
    public Long getCustomDesignId() { return customDesignId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}