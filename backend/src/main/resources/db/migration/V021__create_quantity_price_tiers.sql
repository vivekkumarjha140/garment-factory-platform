-- Quantity-based unit prices for bulk buying. max_qty NULL means "and above".
-- Overlap and ordering rules are checked in the service (they span several rows).
CREATE TABLE quantity_price_tiers (
                                      id         BIGINT         NOT NULL AUTO_INCREMENT,
                                      product_id BIGINT         NOT NULL,
                                      min_qty    INT            NOT NULL,
                                      max_qty    INT            NULL,
                                      unit_price DECIMAL(12, 2) NOT NULL,
                                      created_at DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                      updated_at DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                      PRIMARY KEY (id),
                                      UNIQUE KEY uq_price_tiers_product_min (product_id, min_qty),
                                      CONSTRAINT ck_price_tiers_range CHECK (min_qty >= 1 AND (max_qty IS NULL OR max_qty >= min_qty)),
                                      CONSTRAINT ck_price_tiers_price CHECK (unit_price >= 0),
                                      CONSTRAINT fk_price_tiers_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;