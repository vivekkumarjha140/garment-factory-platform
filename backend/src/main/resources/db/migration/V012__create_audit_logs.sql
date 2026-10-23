-- Append-only record of sensitive actions. No updated_at: rows are never modified.
-- actor_user_id has no foreign key on purpose, so history survives any change to users.
CREATE TABLE audit_logs (
                            id            BIGINT        NOT NULL AUTO_INCREMENT,
                            actor_user_id BIGINT        NULL,
                            action        VARCHAR(60)   NOT NULL,
                            entity_type   VARCHAR(60)   NOT NULL,
                            entity_id     VARCHAR(64)   NULL,
                            details       VARCHAR(1000) NULL,
                            ip            VARCHAR(45)   NULL,
                            user_agent    VARCHAR(255)  NULL,
                            created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                            PRIMARY KEY (id),
                            KEY idx_audit_logs_entity (entity_type, entity_id),
                            KEY idx_audit_logs_actor (actor_user_id, created_at),
                            KEY idx_audit_logs_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;