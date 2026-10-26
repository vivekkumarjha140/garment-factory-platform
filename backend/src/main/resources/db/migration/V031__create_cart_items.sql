CREATE TABLE cart_items (
                            id               BIGINT      NOT NULL AUTO_INCREMENT,
                            cart_id          BIGINT      NOT NULL,
                            variant_id       BIGINT      NOT NULL,
                            custom_design_id BIGINT      NULL,
                            quantity         INT         NOT NULL,
                            line_key         VARCHAR(48) GENERATED ALWAYS AS (CONCAT(variant_id, ':', COALESCE(custom_design_id, 0))) STORED NOT NULL,
                            created_at       DATETIME(6) NOT NULL,
                            updated_at       DATETIME(6) NOT NULL,
                            PRIMARY KEY (id),
                            CONSTRAINT uq_cart_items_line UNIQUE (cart_id, line_key),
                            CONSTRAINT chk_cart_items_qty CHECK (quantity BETWEEN 1 AND 100000),
                            CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
                            CONSTRAINT fk_cart_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;