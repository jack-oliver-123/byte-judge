CREATE TABLE auth_audit (
    id BIGINT NOT NULL,
    userId BIGINT NULL,
    account VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    event VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    outcome VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NULL,
    requestId VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    occurredAt DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    PRIMARY KEY (id),
    KEY ix_auth_audit_user_time (userId, occurredAt),
    CONSTRAINT ck_auth_audit_event CHECK (event IN ('LOGIN', 'LOGOUT', 'IDENTITY_CHECK', 'ADMIN_ACCESS', 'INTERNAL_VERIFY')),
    CONSTRAINT ck_auth_audit_outcome CHECK (outcome IN ('SUCCESS', 'REJECTED', 'DENIED', 'UNAVAILABLE'))
) ENGINE = InnoDB;
