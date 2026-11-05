package com.jack.bytejudge.user.auth.domain;

/** The session identity no longer matches the authoritative account facts. */
public final class SessionRejectedException extends RuntimeException {
    public SessionRejectedException() {
        super("Session identity is no longer valid.");
    }
}
