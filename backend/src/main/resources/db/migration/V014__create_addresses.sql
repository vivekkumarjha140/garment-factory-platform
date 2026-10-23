-- Saved delivery and billing addresses. Orders copy an address as a snapshot, so deleting a saved
-- address never changes history.
-- MySQL has no partial unique indexes, so "at most one default per user" is enforced with two
-- generated columns that equal user_id only for the default row (NULLs never collide in a UNIQUE key).
-- The foreign key deliberately has no CASCADE: MySQL forbids CASCADE on the base column of a stored
-- generated column, and users are never deleted anyway.
CREATE TABLE addresses (
                           id                     BIGINT       NOT NULL AUTO_INCREMENT,
                           public_id              VARCHAR(36)  NOT NULL,
                           user_id                BIGINT       NOT NULL,
                           label                  VARCHAR(30)  NULL,
                           recipient_name         VARCHAR(120) NOT NULL,
                           phone                  VARCHAR(20)  NOT NULL,
                           line1                  VARCHAR(200) NOT NULL,
                           line2                  VARCHAR(200) NULL,
                           city                   VARCHAR(80)  NOT NULL,
                           state                  VARCHAR(60)  NOT NULL,
                           pincode                VARCHAR(6)   NOT NULL,
                           is_default_shipping    TINYINT(1)   NOT NULL DEFAULT 0,
                           is_default_billing     TINYINT(1)   NOT NULL DEFAULT 0,
                           default_shipping_owner BIGINT GENERATED ALWAYS AS (CASE WHEN is_default_shipping = 1 THEN user_id ELSE NULL END) STORED,
                           default_billing_owner  BIGINT GENERATED ALWAYS AS (CASE WHEN is_default_billing = 1 THEN user_id ELSE NULL END) STORED,
                           created_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                           updated_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                           PRIMARY KEY (id),
                           UNIQUE KEY uq_addresses_public_id (public_id),
                           UNIQUE KEY uq_addresses_default_shipping (default_shipping_owner),
                           UNIQUE KEY uq_addresses_default_billing (default_billing_owner),
                           KEY idx_addresses_user (user_id),
                           CONSTRAINT ck_addresses_pincode CHECK (pincode REGEXP '^[1-9][0-9]{5}$'),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;