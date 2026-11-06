package com.jack.bytejudge.user.auth.domain;

/** The session identity may no longer be used; the reason is a fixed classification for auditing. */
public final class SessionRejectedException extends RuntimeException {
    public static final String AUTHORITATIVE_STATE_CHANGED = "AUTHORITATIVE_STATE_CHANGED";
    public static final String SESSION_REVOKED = "SESSION_REVOKED";

    private final String reason;

    public SessionRejectedException() {
        this(AUTHORITATIVE_STATE_CHANGED);
    }

    public SessionRejectedException(String reason) {
        super("Session identity is no longer valid.");
        this.reason = reason;
    }

    public static SessionRejectedException revoked() {
        return new SessionRejectedException(SESSION_REVOKED);
    }

    public String reason() {
        return reason;
    }
}
