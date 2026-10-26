CREATE TABLE zone_rules (
                            id          BIGINT      NOT NULL AUTO_INCREMENT,
                            zone_id     BIGINT      NOT NULL,
                            rule_type   VARCHAR(20) NOT NULL,
                            match_value VARCHAR(64) NOT NULL,
                            created_at  DATETIME(6) NOT NULL,
                            PRIMARY KEY (id),
    -- ek PIN prefix / state sirf ek hi zone mein ho sakta hai (deterministic lookup)
                            CONSTRAINT uq_zone_rules_match UNIQUE (rule_type, match_value),
                            CONSTRAINT chk_zone_rules_type CHECK (rule_type IN ('STATE', 'PIN_PREFIX')),
                            CONSTRAINT fk_zone_rules_zone FOREIGN KEY (zone_id) REFERENCES delivery_zones (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;