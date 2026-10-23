CREATE TABLE permissions (
                             id          BIGINT       NOT NULL AUTO_INCREMENT,
                             code        VARCHAR(80)  NOT NULL,
                             description VARCHAR(255) NULL,
                             created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                             updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                             PRIMARY KEY (id),
                             UNIQUE KEY uq_permissions_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;