-- Editable working copy of a problem. revision is the CAS token: every accepted save increments
-- it, including saves without content changes, so two concurrent editors cannot silently overwrite
-- each other. Content limits are enforced server-side and are never delegated to column lengths.
CREATE TABLE problem_draft (
    id BIGINT NOT NULL,
    problemId BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    title VARCHAR(400) NOT NULL,
    statement MEDIUMTEXT NULL,
    inputDescription MEDIUMTEXT NULL,
    outputDescription MEDIUMTEXT NULL,
    difficulty VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    tags JSON NOT NULL,
    timeLimitCpuMs INT NULL,
    timeLimitWallMs INT NULL,
    memoryLimitMiB INT NULL,
    isDelete TINYINT(1) NOT NULL DEFAULT 0,
    createdById BIGINT NOT NULL,
    updatedById BIGINT NOT NULL,
    createdAt DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    updatedAt DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    PRIMARY KEY (id),
    UNIQUE KEY uk_problem_draft_problem (problemId),
    CONSTRAINT ck_problem_draft_difficulty CHECK (difficulty IS NULL OR difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    CONSTRAINT ck_problem_draft_limits CHECK (
        (timeLimitCpuMs IS NULL OR timeLimitCpuMs BETWEEN 100 AND 10000)
        AND (timeLimitWallMs IS NULL OR timeLimitWallMs BETWEEN 100 AND 30000)
        AND (memoryLimitMiB IS NULL OR memoryLimitMiB BETWEEN 64 AND 2048))
) ENGINE = InnoDB;
