-- Product definition. What is sold (size and colour, SKU, stock) lives in product_variants.
-- Products are never deleted: ARCHIVED hides them while order history keeps pointing at them.
-- gst_rate is data entered by the business per product; the platform assumes no rate.
CREATE TABLE products (
                          id              BIGINT        NOT NULL AUTO_INCREMENT,
                          public_id       VARCHAR(36)   NOT NULL,
                          category_id     BIGINT        NOT NULL,
                          brand_id        BIGINT        NULL,
                          name            VARCHAR(200)  NOT NULL,
                          slug            VARCHAR(220)  NOT NULL,
                          description     VARCHAR(4000) NULL,
                          fabric          VARCHAR(100)  NULL,
                          gsm             INT           NULL,
                          composition     VARCHAR(200)  NULL,
                          hsn_code        VARCHAR(8)    NULL,
                          gst_rate        DECIMAL(5, 2) NOT NULL DEFAULT 5.00,
                          base_price      DECIMAL(12, 2) NOT NULL,
                          mrp             DECIMAL(12, 2) NOT NULL,
                          is_customizable TINYINT(1)    NOT NULL DEFAULT 0,
                          status          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
                          avg_rating      DECIMAL(3, 2) NOT NULL DEFAULT 0.00,
                          review_count    INT           NOT NULL DEFAULT 0,
                          version         BIGINT        NOT NULL DEFAULT 0,
                          created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                          updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                          PRIMARY KEY (id),
                          UNIQUE KEY uq_products_public_id (public_id),
                          UNIQUE KEY uq_products_slug (slug),
                          KEY idx_products_category_status (category_id, status),
                          KEY idx_products_status_created (status, created_at),
                          FULLTEXT KEY ft_products_search (name, description),
                          CONSTRAINT ck_products_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')),
                          CONSTRAINT ck_products_prices CHECK (base_price >= 0 AND mrp >= base_price),
                          CONSTRAINT ck_products_gst CHECK (gst_rate >= 0 AND gst_rate <= 28),
                          CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
                          CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;