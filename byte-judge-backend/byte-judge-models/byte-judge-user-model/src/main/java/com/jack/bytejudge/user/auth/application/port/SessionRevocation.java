package com.jack.bytejudge.user.auth.application.port;

/**
 * Durable revocation facts for individual sessions. A revocation is committed before logout or a
 * rejected identity is acknowledged, and remains the authority even if Redis still contains a copy
 * of the old session. Implementations must be idempotent and must not swallow storage failures:
 * a request that cannot prove a session is still valid must not proceed.
 */
public interface SessionRevocation {
    boolean isRevoked(String sessionId);

    void revoke(String sessionId);
}
