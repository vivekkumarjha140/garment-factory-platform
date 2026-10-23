CREATE TABLE users (
                       id                 BIGINT       NOT NULL AUTO_INCREMENT,
                       public_id          VARCHAR(36)  NOT NULL,
                       email              VARCHAR(254) NOT NULL,
                       password_hash      VARCHAR(255) NOT NULL,
                       full_name          VARCHAR(120) NOT NULL,
                       phone              VARCHAR(20)  NULL,
                       status             VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
                       email_verified_at  DATETIME(6)  NULL,
                       failed_login_count INT          NOT NULL DEFAULT 0,
                       locked_until       DATETIME(6)  NULL,
                       last_login_at      DATETIME(6)  NULL,
                       version            BIGINT       NOT NULL DEFAULT 0,
                       created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                       updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                       PRIMARY KEY (id),
                       UNIQUE KEY uq_users_public_id (public_id),
                       UNIQUE KEY uq_users_email (email),
                       KEY idx_users_status (status),
                       CONSTRAINT ck_users_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'BLOCKED', 'DISABLED')),
                       CONSTRAINT ck_users_failed_login CHECK (failed_login_count >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;