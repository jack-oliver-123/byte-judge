package com.jack.bytejudge.user.auth.domain;

/** The authenticated account exists but has no permission for the requested operation. */
public final class PermissionDeniedException extends RuntimeException {
    public PermissionDeniedException() {
        super("Access denied.");
    }
}
