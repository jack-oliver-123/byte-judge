package com.jack.bytejudge.user.auth.domain;

import java.time.Duration;

/** Login is temporarily denied after the account or source dimension reached its failure threshold. */
public final class LoginRateLimitedException extends RuntimeException {
    private final Duration retryAfter;

    public LoginRateLimitedException(Duration retryAfter) {
        super("Too many login attempts; retry later.");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
