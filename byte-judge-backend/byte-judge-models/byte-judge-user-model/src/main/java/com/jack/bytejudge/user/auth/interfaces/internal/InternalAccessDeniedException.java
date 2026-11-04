package com.jack.bytejudge.user.auth.interfaces.internal;

/** An internal call without the gateway service identity; reported as HTTP 403. */
public final class InternalAccessDeniedException extends RuntimeException {
    public InternalAccessDeniedException() {
        super("Internal caller is not authorized.");
    }
}
