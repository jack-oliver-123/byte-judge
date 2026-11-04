package com.jack.bytejudge.user.auth.domain;

public final class NotAuthenticatedException extends RuntimeException {
    public NotAuthenticatedException() {
        super("Authentication required.");
    }
}
