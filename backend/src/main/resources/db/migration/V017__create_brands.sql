CREATE TABLE brands (
                        id         BIGINT       NOT NULL AUTO_INCREMENT,
                        public_id  VARCHAR(36)  NOT NULL,
                        name       VARCHAR(100) NOT NULL,
                        is_active  TINYINT(1)   NOT NULL DEFAULT 1,
                        created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                        PRIMARY KEY (id),
                        UNIQUE KEY uq_brands_public_id (public_id),
                        UNIQUE KEY uq_brands_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;