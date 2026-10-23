CREATE TABLE verification_tokens (
                                     id         BIGINT      NOT NULL AUTO_INCREMENT,
                                     user_id    BIGINT      NOT NULL,
                                     purpose    VARCHAR(30) NOT NULL,
                                     token_hash VARCHAR(64) NOT NULL,
                                     expires_at DATETIME(6) NOT NULL,
                                     used_at    DATETIME(6) NULL,
                                     created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                     updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                     PRIMARY KEY (id),
                                     UNIQUE KEY uq_verification_tokens_hash (token_hash),
                                     KEY idx_verification_tokens_user (user_id, purpose),
                                     CONSTRAINT ck_verification_tokens_purpose CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
                                     CONSTRAINT fk_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;