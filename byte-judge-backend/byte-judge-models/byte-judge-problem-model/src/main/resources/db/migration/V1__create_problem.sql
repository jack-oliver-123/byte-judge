-- Problem stable identity. problemNo is allocated from a monotonic counter and is never reused,
-- including after logical deletion; logical deletion is only reachable while the problem has no
-- publication history (DRAFT), so everPublished stays false in this slice.
CREATE TABLE problem (
    id BIGINT NOT NULL,
    problemNo INT NOT NULL,
    -- Current editable draft (spec 002 D2). Null only in the same transaction between inserting
    -- the problem and its first draft; the pair is always written together.
    currentDraftId BIGINT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    everPublished TINYINT(1) NOT NULL DEFAULT 0,
    createdById BIGINT NOT NULL,
    createdAt DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    updatedAt DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    isDelete TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_problem_problemNo (problemNo),
    CONSTRAINT ck_problem_status CHECK (status IN ('DRAFT', 'DELETED')),
    CONSTRAINT ck_problem_problemNo CHECK (problemNo >= 1000)
) ENGINE = InnoDB;

-- Monotonic problem-number allocator. The row is locked with SELECT ... FOR UPDATE inside the
-- creation transaction, so concurrent creations receive distinct numbers without AUTO_INCREMENT.
CREATE TABLE problem_no_sequence (
    id TINYINT NOT NULL,
    nextValue INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_problem_no_sequence_singleton CHECK (id = 1)
) ENGINE = InnoDB;

INSERT INTO problem_no_sequence (id, nextValue) VALUES (1, 1000);
