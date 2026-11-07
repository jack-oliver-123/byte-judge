package com.jack.bytejudge.user.auth.interfaces.internal;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Requires a dedicated internal service identity on internal calls. A browser session, an
 * {@code X-User-*} header or a role from the public API never satisfies this check. Each caller
 * service has its own high-entropy credential from the controlled environment; the map is fixed
 * at startup and credentials are never logged or echoed.
 */
public final class InternalCallGuard {
    static final String CALLER_HEADER = "X-Internal-Service";
    static final String CREDENTIAL_HEADER = "X-Internal-Credential";

    private final Map<String, byte[]> expectedCredentials;

    public InternalCallGuard(Map<String, String> credentialsByCaller) {
        if (credentialsByCaller == null || credentialsByCaller.isEmpty()) {
            throw new IllegalStateException(
                    "At least one internal caller credential must be configured.");
        }
        this.expectedCredentials = credentialsByCaller.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> {
                    String credential = entry.getValue();
                    if (credential == null || credential.length() < 32) {
                        throw new IllegalStateException(
                                "The internal credential for caller '" + entry.getKey()
                                        + "' must be at least 32 characters.");
                    }
                    return credential.getBytes(StandardCharsets.UTF_8);
                }));
    }

    public boolean accepts(HttpServletRequest request) {
        String caller = request.getHeader(CALLER_HEADER);
        if (caller == null) {
            return false;
        }
        byte[] expected = expectedCredentials.get(caller);
        String supplied = request.getHeader(CREDENTIAL_HEADER);
        return expected != null && supplied != null
                && MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8));
    }
}
