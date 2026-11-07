package com.jack.bytejudge.problem.admin.domain;

/** Defensive exit for a protected controller reached without an authenticated session identity. */
public final class AdminIdentityRequiredException extends RuntimeException {
    public AdminIdentityRequiredException() {
        super("An administrator session is required.", null, false, false);
    }
}
