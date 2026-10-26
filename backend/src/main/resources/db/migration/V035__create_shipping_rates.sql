CREATE TABLE shipping_rates (
                                id                 BIGINT        NOT NULL AUTO_INCREMENT,
                                zone_id            BIGINT        NOT NULL,
                                min_weight_grams   INT           NOT NULL,
                                max_weight_grams   INT           NOT NULL,
                                charge             DECIMAL(12,2) NOT NULL,
                                estimated_days_min SMALLINT      NOT NULL DEFAULT 3,
                                estimated_days_max SMALLINT      NOT NULL DEFAULT 7,
                                active             TINYINT(1)    NOT NULL DEFAULT 1,
                                created_at         DATETIME(6)   NOT NULL,
                                updated_at         DATETIME(6)   NOT NULL,
                                PRIMARY KEY (id),
                                CONSTRAINT uq_shipping_rates_slab UNIQUE (zone_id, min_weight_grams),
                                CONSTRAINT chk_shipping_rates_weight CHECK (min_weight_grams >= 0 AND max_weight_grams > min_weight_grams),
                                CONSTRAINT chk_shipping_rates_charge CHECK (charge >= 0),
                                CONSTRAINT chk_shipping_rates_days CHECK (estimated_days_min >= 0 AND estimated_days_max >= estimated_days_min),
                                CONSTRAINT fk_shipping_rates_zone FOREIGN KEY (zone_id) REFERENCES delivery_zones (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;