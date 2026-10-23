CREATE TABLE inventory_audits (
                                  id                  BIGINT        NOT NULL AUTO_INCREMENT,
                                  public_id           CHAR(36)      NOT NULL,
                                  audit_number        VARCHAR(30)   NOT NULL,
                                  status              VARCHAR(20)   NOT NULL DEFAULT 'IN_PROGRESS',
                                  notes               VARCHAR(500)  NULL,
                                  started_by_user_id  BIGINT        NOT NULL,
                                  started_at          DATETIME(6)   NOT NULL,
                                  completed_at        DATETIME(6)   NULL,
                                  created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                  updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                  PRIMARY KEY (id),
                                  UNIQUE KEY uk_inventory_audits_public_id (public_id),
                                  UNIQUE KEY uk_inventory_audits_number (audit_number),
                                  CONSTRAINT fk_ia_user FOREIGN KEY (started_by_user_id) REFERENCES users (id),
                                  CONSTRAINT chk_ia_status CHECK (status IN ('IN_PROGRESS','COMPLETED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory_audit_lines (
                                       id                     BIGINT         NOT NULL AUTO_INCREMENT,
                                       audit_id               BIGINT         NOT NULL,
                                       item_type              VARCHAR(20)    NOT NULL,
                                       variant_id             BIGINT         NULL,
                                       raw_material_id        BIGINT         NULL,
                                       system_quantity        DECIMAL(14,3)  NOT NULL,
                                       counted_quantity       DECIMAL(14,3)  NOT NULL,
                                       variance               DECIMAL(14,3)  NOT NULL,
                                       adjustment_movement_id BIGINT         NULL,
                                       created_at             DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                       PRIMARY KEY (id),
                                       KEY idx_ial_audit (audit_id),
                                       CONSTRAINT fk_ial_audit FOREIGN KEY (audit_id) REFERENCES inventory_audits (id),
                                       CONSTRAINT fk_ial_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
                                       CONSTRAINT fk_ial_raw FOREIGN KEY (raw_material_id) REFERENCES raw_materials (id),
                                       CONSTRAINT fk_ial_movement FOREIGN KEY (adjustment_movement_id) REFERENCES stock_movements (id),
                                       CONSTRAINT chk_ial_item_type CHECK (item_type IN ('FINISHED_GOOD','RAW_MATERIAL')),
                                       CONSTRAINT chk_ial_target CHECK (
                                           (item_type = 'FINISHED_GOOD' AND variant_id IS NOT NULL AND raw_material_id IS NULL) OR
                                           (item_type = 'RAW_MATERIAL'  AND raw_material_id IS NOT NULL AND variant_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;