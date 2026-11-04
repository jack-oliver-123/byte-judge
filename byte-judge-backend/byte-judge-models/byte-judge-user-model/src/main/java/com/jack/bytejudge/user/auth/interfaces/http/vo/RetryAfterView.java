package com.jack.bytejudge.user.auth.interfaces.http.vo;

/** The only data a rate-limited caller needs: how long to wait before the next login attempt. */
public record RetryAfterView(long retryAfterSeconds) { }
