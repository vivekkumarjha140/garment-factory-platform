-- Key/value runtime settings (reservation TTL, thresholds, seller details).
-- One schema change per migration file: MySQL DDL is not transactional.
CREATE TABLE app_settings (
                              id            BIGINT       NOT NULL AUTO_INCREMENT,
                              setting_key   VARCHAR(100) NOT NULL,
                              setting_value JSON         NOT NULL,
                              description   VARCHAR(255) NULL,
                              created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                              updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                              PRIMARY KEY (id),
                              UNIQUE KEY uq_app_settings_key (setting_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;