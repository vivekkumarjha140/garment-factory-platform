CREATE TABLE delivery_zones (
                                id                   BIGINT        NOT NULL AUTO_INCREMENT,
                                code                 VARCHAR(30)   NOT NULL,
                                name                 VARCHAR(100)  NOT NULL,
                                free_shipping_above  DECIMAL(12,2) NULL,
                                active               TINYINT(1)    NOT NULL DEFAULT 1,
                                created_at           DATETIME(6)   NOT NULL,
                                updated_at           DATETIME(6)   NOT NULL,
                                PRIMARY KEY (id),
                                CONSTRAINT uq_delivery_zones_code UNIQUE (code),
                                CONSTRAINT chk_delivery_zones_free CHECK (free_shipping_above IS NULL OR free_shipping_above >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;