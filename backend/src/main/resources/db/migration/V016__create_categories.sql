-- Category tree (at most 3 levels, enforced by the service). Categories are deactivated, never deleted.
CREATE TABLE categories (
                            id          BIGINT       NOT NULL AUTO_INCREMENT,
                            public_id   VARCHAR(36)  NOT NULL,
                            parent_id   BIGINT       NULL,
                            name        VARCHAR(100) NOT NULL,
                            slug        VARCHAR(120) NOT NULL,
                            sort_order  INT          NOT NULL DEFAULT 0,
                            is_active   TINYINT(1)   NOT NULL DEFAULT 1,
                            created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                            updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                            PRIMARY KEY (id),
                            UNIQUE KEY uq_categories_public_id (public_id),
                            UNIQUE KEY uq_categories_slug (slug),
                            KEY idx_categories_parent (parent_id),
                            CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;