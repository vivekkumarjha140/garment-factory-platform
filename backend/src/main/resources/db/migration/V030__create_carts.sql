CREATE TABLE carts (
                       id          BIGINT       NOT NULL AUTO_INCREMENT,
                       user_id     BIGINT       NOT NULL,
                       coupon_code VARCHAR(40)  NULL,
                       version     BIGINT       NOT NULL DEFAULT 0,
                       created_at  DATETIME(6)  NOT NULL,
                       updated_at  DATETIME(6)  NOT NULL,
                       PRIMARY KEY (id),
                       CONSTRAINT uq_carts_user UNIQUE (user_id),
                       CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;