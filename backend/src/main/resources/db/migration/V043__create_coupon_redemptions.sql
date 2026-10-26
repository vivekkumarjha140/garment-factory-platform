-- One redemption per order. Counting ACTIVE rows per (coupon, user) enforces usage_limit_per_user.
CREATE TABLE coupon_redemptions (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    coupon_id       BIGINT        NOT NULL,
    user_id         BIGINT        NOT NULL,
    order_id        BIGINT        NOT NULL,
    discount_amount DECIMAL(12,2) NOT NULL,
    status          VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    released_at     DATETIME(6)   NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_coupon_redemptions_order (order_id),
    KEY idx_coupon_redemptions_usage (coupon_id, user_id, status),
    CONSTRAINT fk_coupon_redemptions_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    CONSTRAINT fk_coupon_redemptions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_coupon_redemptions_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_coupon_redemptions_status CHECK (status IN ('ACTIVE','RELEASED')),
    CONSTRAINT ck_coupon_redemptions_discount CHECK (discount_amount >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
