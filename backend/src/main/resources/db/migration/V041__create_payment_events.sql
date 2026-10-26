-- Webhook ledger. The unique key is what makes webhook processing idempotent (a replayed
-- provider event can be stored only once).
CREATE TABLE payment_events (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    provider          VARCHAR(20)  NOT NULL,
    provider_event_id VARCHAR(100) NOT NULL,
    event_type        VARCHAR(80)  NOT NULL,
    payload           JSON         NOT NULL,
    signature_valid   TINYINT(1)   NOT NULL,
    received_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at      DATETIME(6)  NULL,
    processing_error  VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_payment_events_provider_event (provider, provider_event_id),
    KEY idx_payment_events_received (received_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
