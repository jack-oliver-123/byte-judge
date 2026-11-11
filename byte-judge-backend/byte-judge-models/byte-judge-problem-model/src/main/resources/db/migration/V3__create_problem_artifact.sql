-- Test-case file manifest. Files live on a protected volume addressed by content digest; the path
-- is resolved only inside the service and never appears in an API response. Uploads write a
-- temporary file, verify the digest, atomically rename, then commit this row in the same
-- transaction as the draft revision advance.
--
-- Slot semantics (frozen for this slice): (draftId, kind, caseOrder) identifies one manifest slot.
-- A second upload to an occupied live slot is rejected explicitly; it never replaces silently.
-- The same content may still be referenced by several rows (for example different caseOrder), so
-- contentDigest deliberately has no unique constraint. Logical deletion is not part of this slice;
-- if it is added, this key must be revisited because MySQL has no partial unique index.
--
-- FR-12 also caps the distinct test-case groups at 20, counting sample input/output as one group
-- and hidden input/output as another. That cross-row rule is enforced by artifact registration
-- under the same draft row lock that serialises uploads; byteSize may be 0 because FR-12 permits
-- an empty input or expected output as long as the field itself exists.
CREATE TABLE problem_artifact (
    id BIGINT NOT NULL,
    problemId BIGINT NOT NULL,
    draftId BIGINT NOT NULL,
    contentDigest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    byteSize BIGINT NOT NULL,
    fileName VARCHAR(255) NOT NULL,
    kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    caseOrder INT NOT NULL,
    displayOrder INT NOT NULL DEFAULT 0,
    isDelete TINYINT(1) NOT NULL DEFAULT 0,
    createdAt DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    updatedAt DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    PRIMARY KEY (id),
    UNIQUE KEY uk_problem_artifact_slot (draftId, kind, caseOrder),
    KEY ix_problem_artifact_problem (problemId, isDelete),
    CONSTRAINT ck_problem_artifact_kind CHECK (kind IN ('SAMPLE_INPUT', 'SAMPLE_OUTPUT', 'HIDDEN_INPUT', 'HIDDEN_OUTPUT')),
    CONSTRAINT ck_problem_artifact_size CHECK (byteSize >= 0 AND byteSize <= 1048576),
    CONSTRAINT ck_problem_artifact_digest CHECK (REGEXP_LIKE(contentDigest, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT ck_problem_artifact_caseOrder CHECK (caseOrder BETWEEN 1 AND 20),
    CONSTRAINT ck_problem_artifact_displayOrder CHECK (displayOrder BETWEEN 0 AND 10000)
) ENGINE = InnoDB;
