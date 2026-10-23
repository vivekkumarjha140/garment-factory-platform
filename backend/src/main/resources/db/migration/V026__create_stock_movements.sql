CREATE TABLE stock_movements (
                                 id                    BIGINT         NOT NULL AUTO_INCREMENT,
                                 public_id             CHAR(36)       NOT NULL,
                                 item_type             VARCHAR(20)    NOT NULL,
                                 variant_id            BIGINT         NULL,
                                 raw_material_id       BIGINT         NULL,
                                 movement_type         VARCHAR(20)    NOT NULL,
                                 on_hand_delta         DECIMAL(14,3)  NOT NULL DEFAULT 0,
                                 reserved_delta        DECIMAL(14,3)  NOT NULL DEFAULT 0,
                                 damaged_delta         DECIMAL(14,3)  NOT NULL DEFAULT 0,
                                 balance_on_hand_after DECIMAL(14,3)  NOT NULL,
                                 reference_type        VARCHAR(40)    NULL,
                                 reference_id          VARCHAR(64)    NULL,
                                 reason                VARCHAR(500)   NULL,
                                 actor_user_id         BIGINT         NULL,
                                 created_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                 PRIMARY KEY (id),
                                 UNIQUE KEY uk_stock_movements_public_id (public_id),
                                 KEY idx_sm_variant_created (variant_id, created_at),
                                 KEY idx_sm_raw_created (raw_material_id, created_at),
                                 KEY idx_sm_reference (reference_type, reference_id),
                                 KEY idx_sm_type (movement_type),
                                 CONSTRAINT fk_sm_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
                                 CONSTRAINT fk_sm_raw FOREIGN KEY (raw_material_id) REFERENCES raw_materials (id),
                                 CONSTRAINT fk_sm_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
                                 CONSTRAINT chk_sm_item_type CHECK (item_type IN ('FINISHED_GOOD','RAW_MATERIAL')),
                                 CONSTRAINT chk_sm_movement_type CHECK (movement_type IN
                                                                        ('RECEIPT','RESERVATION','DEDUCTION','RELEASE','ADJUSTMENT','DAMAGE')),
                                 CONSTRAINT chk_sm_target CHECK (
                                     (item_type = 'FINISHED_GOOD' AND variant_id IS NOT NULL AND raw_material_id IS NULL) OR
                                     (item_type = 'RAW_MATERIAL'  AND raw_material_id IS NOT NULL AND variant_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;