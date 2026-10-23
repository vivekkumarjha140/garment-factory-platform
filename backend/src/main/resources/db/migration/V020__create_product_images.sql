-- Image metadata. The files live in object storage (see Step 4.3). At most one primary image per
-- product, enforced by a generated column (MySQL has no partial unique indexes).
CREATE TABLE product_images (
                                id            BIGINT       NOT NULL AUTO_INCREMENT,
                                public_id     VARCHAR(36)  NOT NULL,
                                product_id    BIGINT       NOT NULL,
                                variant_id    BIGINT       NULL,
                                object_key    VARCHAR(255) NOT NULL,
                                alt_text      VARCHAR(200) NULL,
                                sort_order    INT          NOT NULL DEFAULT 0,
                                is_primary    TINYINT(1)   NOT NULL DEFAULT 0,
                                primary_owner BIGINT GENERATED ALWAYS AS (CASE WHEN is_primary = 1 THEN product_id ELSE NULL END) STORED,
                                created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                PRIMARY KEY (id),
                                UNIQUE KEY uq_product_images_public_id (public_id),
                                UNIQUE KEY uq_product_images_object_key (object_key),
                                UNIQUE KEY uq_product_images_primary (primary_owner),
                                KEY idx_product_images_product (product_id, sort_order),
                                CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id),
                                CONSTRAINT fk_product_images_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;