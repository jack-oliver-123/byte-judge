-- Durable per-session revocation facts. A tombstone is the authority for whether a session id may
-- still authenticate; deleting the Redis hash is only cleanup.
--
-- Rows are never removed automatically. The 30-minute idle timeout bounds inactivity, not the
-- lifetime of an in-flight request that already loaded the session, so it cannot justify dropping a
-- tombstone. Reclaiming rows later requires a proven maximum request lifetime (or an absolute
-- session lifetime) and a retention window that exceeds it.
CREATE TABLE revoked_session (
    id BIGINT NOT NULL,
    -- SHA-256 of the session identifier. The raw cookie value is never persisted.
    sessionIdHash BINARY(32) NOT NULL,
    revokedAt DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    PRIMARY KEY (id),
    UNIQUE KEY uk_revoked_session_hash (sessionIdHash)
) ENGINE = InnoDB;
