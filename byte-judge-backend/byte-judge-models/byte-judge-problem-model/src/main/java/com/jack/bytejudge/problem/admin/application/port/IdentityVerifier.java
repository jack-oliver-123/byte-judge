package com.jack.bytejudge.problem.admin.application.port;

import com.jack.bytejudge.problem.admin.domain.model.VerifiedIdentity;

/**
 * Verifies a session identifier against the user service's authoritative storage. Implementations
 * fail closed: an unavailable user service or an unusable answer refuses the request rather than
 * allowing it through.
 */
public interface IdentityVerifier {

    /**
     * Returns the verified identity for a live session, or empty when the session is absent,
     * revoked or no longer matches the account's current state.
     */
    java.util.Optional<VerifiedIdentity> verify(String sessionId, String path, String requestId);
}
