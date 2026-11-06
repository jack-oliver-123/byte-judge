CREATE TABLE user_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    auth_epoch BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_account_account UNIQUE (account),
    CONSTRAINT ck_user_account_account CHECK (REGEXP_LIKE(account, '^[a-z0-9_]{4,32}$', 'c')),
    CONSTRAINT ck_user_account_role CHECK (role IN ('user', 'admin')),
    CONSTRAINT ck_user_account_status CHECK (status IN ('normal', 'disabled')),
    CONSTRAINT ck_user_account_auth_epoch CHECK (auth_epoch >= 0)
) ENGINE = InnoDB;
