-- One profile per customer account. The user's name, email and phone stay in the users table
-- (identity module); this table holds only customer-specific settings.
CREATE TABLE customer_profiles (
                                   id               BIGINT      NOT NULL AUTO_INCREMENT,
                                   user_id          BIGINT      NOT NULL,
                                   customer_type    VARCHAR(20) NOT NULL DEFAULT 'RETAIL',
                                   marketing_opt_in TINYINT(1)  NOT NULL DEFAULT 0,
                                   created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                   updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                                   PRIMARY KEY (id),
                                   UNIQUE KEY uq_customer_profiles_user (user_id),
                                   CONSTRAINT ck_customer_profiles_type CHECK (customer_type IN ('RETAIL', 'BUSINESS')),
                                   CONSTRAINT fk_customer_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;