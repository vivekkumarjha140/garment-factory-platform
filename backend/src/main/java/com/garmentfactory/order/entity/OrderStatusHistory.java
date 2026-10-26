package com.garmentfactory.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One status change of one dimension. Append-only (see V044). */
@Entity
@Table(name = "order_status_history")
@Getter
@NoArgsConstructor
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private StatusDimension dimension;

    @Column(name = "from_status", updatable = false, length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, updatable = false, length = 20)
    private String toStatus;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(updatable = false, length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static OrderStatusHistory of(long orderId, StatusDimension dimension, String from, String to,
                                        Long actorUserId, String reason) {
        OrderStatusHistory row = new OrderStatusHistory();
        row.orderId = orderId;
        row.dimension = dimension;
        row.fromStatus = from;
        row.toStatus = to;
        row.actorUserId = actorUserId;
        row.reason = reason;
        return row;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}