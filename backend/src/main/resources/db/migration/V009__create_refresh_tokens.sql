CREATE TABLE refresh_tokens (
                                id             BIGINT       NOT NULL AUTO_INCREMENT,
                                user_id        BIGINT       NOT NULL,
                                family_id      VARCHAR(36)  NOT NULL,
                                token_hash     VARCHAR(64)  NOT NULL,
                                device_info    VARCHAR(255) NULL,
                                ip             VARCHAR(45)  NULL,
                                expires_at     DATETIME(6)  NOT NULL,
                                rotated_at     DATETIME(6)  NULL,
                                revoked_at     DATETIME(6)  NULL,
                                replaced_by_id BIGINT       NULL,
                                created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                PRIMARY KEY (id),
                                UNIQUE KEY uq_refresh_tokens_hash (token_hash),
                                KEY idx_refresh_tokens_user (user_id, revoked_at),
                                KEY idx_refresh_tokens_family (family_id),
                                KEY idx_refresh_tokens_expires (expires_at),
                                CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
                                CONSTRAINT fk_refresh_tokens_replaced FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;