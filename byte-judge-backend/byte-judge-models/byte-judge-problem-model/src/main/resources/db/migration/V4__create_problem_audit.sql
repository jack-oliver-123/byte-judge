-- Management audit for problem administration. Mirrors the user service audit shape: no request
-- bodies, no statement text, no file contents -- only who, what action, which object, when.
CREATE TABLE problem_audit (
    id BIGINT NOT NULL,
    operatorId BIGINT NULL,
    operatorAccount VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    event VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    outcome VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NULL,
    problemId BIGINT NULL,
    requestId VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    occurredAt DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    PRIMARY KEY (id),
    KEY ix_problem_audit_problem_time (problemId, occurredAt),
    CONSTRAINT ck_problem_audit_event CHECK (event IN ('PROBLEM_CREATE', 'PROBLEM_UPDATE', 'PROBLEM_DELETE', 'ADMIN_ACCESS', 'ARTIFACT_UPLOAD')),
    CONSTRAINT ck_problem_audit_outcome CHECK (outcome IN ('SUCCESS', 'REJECTED', 'DENIED'))
) ENGINE = InnoDB;
