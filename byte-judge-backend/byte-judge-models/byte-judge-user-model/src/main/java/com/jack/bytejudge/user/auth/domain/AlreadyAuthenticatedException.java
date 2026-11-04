package com.jack.bytejudge.user.auth.domain;

public final class AlreadyAuthenticatedException extends RuntimeException {
    public AlreadyAuthenticatedException() {
        super("Log out before signing in again.");
    }
}
