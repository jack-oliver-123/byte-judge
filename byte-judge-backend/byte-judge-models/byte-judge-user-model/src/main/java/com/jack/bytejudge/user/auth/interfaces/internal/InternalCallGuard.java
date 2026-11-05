package com.jack.bytejudge.user.auth.interfaces.internal;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Requires the dedicated gateway service identity on internal calls. A browser session, an
 * {@code X-User-*} header or a role from the public API never satisfies this check. The credential
 * is supplied by the controlled environment and is never logged or echoed.
 */
public final class InternalCallGuard {
    static final String CALLER_HEADER = "X-Internal-Service";
    static final String CREDENTIAL_HEADER = "X-Internal-Credential";
    private static final String GATEWAY = "gateway";

    private final byte[] expectedCredential;

    public InternalCallGuard(String credential) {
        if (credential == null || credential.length() < 32) {
            throw new IllegalStateException(
                    "byte-judge.internal.gateway-credential must be at least 32 characters.");
        }
        this.expectedCredential = credential.getBytes(StandardCharsets.UTF_8);
    }

    public boolean accepts(HttpServletRequest request) {
        String caller = request.getHeader(CALLER_HEADER);
        String supplied = request.getHeader(CREDENTIAL_HEADER);
        return GATEWAY.equals(caller) && supplied != null
                && MessageDigest.isEqual(expectedCredential, supplied.getBytes(StandardCharsets.UTF_8));
    }
}
