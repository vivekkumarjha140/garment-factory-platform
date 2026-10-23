-- Gap-free document numbers (invoices, orders, quotations).
-- Incremented under a row lock inside the business transaction.
CREATE TABLE number_sequences (
                                  seq_key    VARCHAR(50) NOT NULL,
                                  next_value BIGINT      NOT NULL DEFAULT 1,
                                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                  PRIMARY KEY (seq_key),
                                  CONSTRAINT ck_number_sequences_positive CHECK (next_value >= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;