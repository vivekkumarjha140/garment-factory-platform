CREATE TABLE coupons (
                         id                   BIGINT        NOT NULL AUTO_INCREMENT,
                         code                 VARCHAR(40)   NOT NULL,
                         description          VARCHAR(255)  NULL,
                         discount_type        VARCHAR(20)   NOT NULL,
                         discount_value       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                         max_discount         DECIMAL(12,2) NULL,
                         min_order_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                         valid_from           DATETIME(6)   NOT NULL,
                         valid_until          DATETIME(6)   NULL,
                         usage_limit_total    INT           NULL,
                         usage_limit_per_user INT           NULL,
                         times_used           INT           NOT NULL DEFAULT 0,
                         active               TINYINT(1)    NOT NULL DEFAULT 1,
                         version              BIGINT        NOT NULL DEFAULT 0,
                         created_at           DATETIME(6)   NOT NULL,
                         updated_at           DATETIME(6)   NOT NULL,
                         PRIMARY KEY (id),
                         CONSTRAINT uq_coupons_code UNIQUE (code),
                         CONSTRAINT chk_coupons_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT', 'FREE_SHIPPING')),
                         CONSTRAINT chk_coupons_value CHECK (discount_value >= 0),
                         CONSTRAINT chk_coupons_percent CHECK (discount_type <> 'PERCENTAGE' OR discount_value <= 100),
                         CONSTRAINT chk_coupons_max CHECK (max_discount IS NULL OR max_discount >= 0),
                         CONSTRAINT chk_coupons_min CHECK (min_order_amount >= 0),
                         CONSTRAINT chk_coupons_window CHECK (valid_until IS NULL OR valid_until > valid_from),
                         CONSTRAINT chk_coupons_limits CHECK (
                             (usage_limit_total IS NULL OR usage_limit_total > 0)
                                 AND (usage_limit_per_user IS NULL OR usage_limit_per_user > 0)
                             ),
                         CONSTRAINT chk_coupons_used CHECK (
                             times_used >= 0 AND (usage_limit_total IS NULL OR times_used <= usage_limit_total)
                             )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;