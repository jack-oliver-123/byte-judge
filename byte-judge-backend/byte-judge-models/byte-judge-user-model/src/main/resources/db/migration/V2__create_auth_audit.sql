CREATE TABLE auth_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    account VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    event VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    outcome VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NULL,
    request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_auth_audit_user_time (user_id, occurred_at),
    CONSTRAINT ck_auth_audit_event CHECK (event IN ('LOGIN', 'LOGOUT', 'IDENTITY_CHECK', 'ADMIN_ACCESS', 'INTERNAL_VERIFY')),
    CONSTRAINT ck_auth_audit_outcome CHECK (outcome IN ('SUCCESS', 'REJECTED', 'DENIED', 'UNAVAILABLE'))
) ENGINE = InnoDB;
