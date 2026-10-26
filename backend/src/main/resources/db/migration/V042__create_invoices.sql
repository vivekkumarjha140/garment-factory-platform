-- Tax invoice snapshot, one per order. pdf_file_id has no foreign key: there is no files table yet.
CREATE TABLE invoices (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    invoice_number  VARCHAR(30)   NOT NULL,
    order_id        BIGINT        NOT NULL,
    payment_id      BIGINT        NULL,
    issued_at       DATETIME(6)   NOT NULL,
    seller_state    VARCHAR(60)   NOT NULL,
    place_of_supply VARCHAR(60)   NOT NULL,
    gst_type        VARCHAR(12)   NOT NULL,
    subtotal        DECIMAL(12,2) NOT NULL,
    discount_total  DECIMAL(12,2) NOT NULL,
    shipping_total  DECIMAL(12,2) NOT NULL,
    tax_total       DECIMAL(12,2) NOT NULL,
    cgst_total      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    sgst_total      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    igst_total      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    grand_total     DECIMAL(12,2) NOT NULL,
    pdf_file_id     BIGINT        NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_invoices_number (invoice_number),
    UNIQUE KEY uq_invoices_order (order_id),
    KEY idx_invoices_payment (payment_id),
    CONSTRAINT fk_invoices_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_invoices_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT ck_invoices_gst_type CHECK (gst_type IN ('INTRA_STATE','INTER_STATE')),
    CONSTRAINT ck_invoices_tax_split CHECK (tax_total = cgst_total + sgst_total + igst_total),
    CONSTRAINT ck_invoices_grand_total CHECK (grand_total = subtotal - discount_total + tax_total + shipping_total)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
