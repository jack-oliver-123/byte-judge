package com.jack.bytejudge.gateway.identity;

/** Entry check against the user service's authoritative session and account facts. */
public interface IdentityVerifier {
    VerificationResult verify(String sessionId, String path, String requestId);

    /** Status is the user service decision; role is present only for an allowed identity. */
    record VerificationResult(int status, String role) {
    }
}
