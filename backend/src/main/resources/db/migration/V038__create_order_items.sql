-- Frozen line snapshot: name, sku, size, colour, price and tax never change after the order exists.
-- line_total = unit_price * quantity - discount_amount + tax_amount.
CREATE TABLE order_items (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    order_id        BIGINT        NOT NULL,
    variant_id      BIGINT        NOT NULL,
    product_name    VARCHAR(200)  NOT NULL,
    sku             VARCHAR(64)   NOT NULL,
    size            VARCHAR(20)   NOT NULL,
    color           VARCHAR(50)   NOT NULL,
    unit_price      DECIMAL(12,2) NOT NULL,
    quantity        INT           NOT NULL,
    discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    tax_rate        DECIMAL(5,2)  NOT NULL,
    tax_amount      DECIMAL(12,2) NOT NULL,
    line_total      DECIMAL(12,2) NOT NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_order_items_order (order_id),
    KEY idx_order_items_variant (variant_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_amounts CHECK (unit_price >= 0 AND discount_amount >= 0 AND tax_amount >= 0
        AND discount_amount <= unit_price * quantity),
    CONSTRAINT ck_order_items_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 28),
    CONSTRAINT ck_order_items_line_total CHECK (line_total = unit_price * quantity - discount_amount + tax_amount)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
