-- One row per payment attempt. Used from Phase 7.2 (Razorpay) and 7.3 (COD).
CREATE TABLE payments (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    public_id           VARCHAR(36)   NOT NULL,
    order_id            BIGINT        NOT NULL,
    payment_type        VARCHAR(20)   NOT NULL,
    provider            VARCHAR(20)   NOT NULL,
    provider_order_id   VARCHAR(64)   NULL,
    provider_payment_id VARCHAR(64)   NULL,
    amount              DECIMAL(12,2) NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'CREATED',
    idempotency_key     VARCHAR(64)   NOT NULL,
    failure_reason      VARCHAR(255)  NULL,
    captured_at         DATETIME(6)   NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_payments_public_id (public_id),
    UNIQUE KEY uq_payments_idempotency (idempotency_key),
    UNIQUE KEY uq_payments_provider_order (provider, provider_order_id),
    UNIQUE KEY uq_payments_provider_payment (provider, provider_payment_id),
    KEY idx_payments_order (order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_payments_type CHECK (payment_type IN ('FULL','ADVANCE','BALANCE','COD','REFUND')),
    CONSTRAINT ck_payments_provider CHECK (provider IN ('RAZORPAY','COD')),
    CONSTRAINT ck_payments_status CHECK (status IN ('CREATED','AUTHORIZED','CAPTURED','FAILED','REFUNDED')),
    CONSTRAINT ck_payments_amount CHECK (amount > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
