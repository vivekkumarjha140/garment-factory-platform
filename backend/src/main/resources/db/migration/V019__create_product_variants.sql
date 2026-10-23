-- The sellable unit. The SKU is globally unique and never changes. The collation is case-insensitive,
-- so "Red" and "red" count as the same colour for the (product, size, colour) uniqueness rule.
CREATE TABLE product_variants (
                                  id             BIGINT         NOT NULL AUTO_INCREMENT,
                                  public_id      VARCHAR(36)    NOT NULL,
                                  product_id     BIGINT         NOT NULL,
                                  sku            VARCHAR(64)    NOT NULL,
                                  size           VARCHAR(20)    NOT NULL,
                                  color_name     VARCHAR(50)    NOT NULL,
                                  color_hex      VARCHAR(7)     NULL,
                                  price_override DECIMAL(12, 2) NULL,
                                  weight_g       INT            NULL,
                                  barcode        VARCHAR(32)    NULL,
                                  status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
                                  version        BIGINT         NOT NULL DEFAULT 0,
                                  created_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                  updated_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                  PRIMARY KEY (id),
                                  UNIQUE KEY uq_variants_public_id (public_id),
                                  UNIQUE KEY uq_variants_sku (sku),
                                  UNIQUE KEY uq_variants_product_size_color (product_id, size, color_name),
                                  UNIQUE KEY uq_variants_barcode (barcode),
                                  KEY idx_variants_product_status (product_id, status),
                                  CONSTRAINT ck_variants_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
                                  CONSTRAINT ck_variants_price CHECK (price_override IS NULL OR price_override >= 0),
                                  CONSTRAINT fk_variants_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;