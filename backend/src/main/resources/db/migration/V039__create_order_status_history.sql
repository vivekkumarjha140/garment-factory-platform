-- Append-only trail of every status change, one row per change, per dimension.
-- actor_user_id has no foreign key on purpose (history must survive any change to users).
CREATE TABLE order_status_history (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    order_id      BIGINT       NOT NULL,
    dimension     VARCHAR(20)  NOT NULL,
    from_status   VARCHAR(20)  NULL,
    to_status     VARCHAR(20)  NOT NULL,
    actor_user_id BIGINT       NULL,
    reason        VARCHAR(255) NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_order_status_history_order (order_id, id),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_order_status_history_dimension CHECK (dimension IN ('ORDER','PAYMENT','PRODUCTION','SHIPMENT'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
