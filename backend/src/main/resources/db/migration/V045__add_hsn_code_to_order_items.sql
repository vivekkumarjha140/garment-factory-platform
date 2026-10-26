-- HSN code frozen on the line, like price and tax. Null for orders placed before this column existed.
ALTER TABLE order_items ADD COLUMN hsn_code VARCHAR(8) NULL AFTER sku;
