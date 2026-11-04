package com.jack.bytejudge.user.auth.infrastructure.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Production defaults are 10 failures inside a 15-minute window and a 15-minute block. The local
 * test profile lowers the values only through explicit overrides so acceptance tests never sleep
 * for minutes.
 */
@ConfigurationProperties("byte-judge.auth.rate-limit")
public record LoginRateLimitProperties(Duration window, int maxFailures, Duration blockDuration, String namespace) {
    public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);
    public static final int DEFAULT_MAX_FAILURES = 10;
    public static final Duration DEFAULT_BLOCK_DURATION = Duration.ofMinutes(15);
    public static final String DEFAULT_NAMESPACE = "byte-judge:auth:login";

    public LoginRateLimitProperties {
        window = window == null ? DEFAULT_WINDOW : window;
        maxFailures = maxFailures == 0 ? DEFAULT_MAX_FAILURES : maxFailures;
        blockDuration = blockDuration == null ? DEFAULT_BLOCK_DURATION : blockDuration;
        namespace = namespace == null || namespace.isBlank() ? DEFAULT_NAMESPACE : namespace;
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Login rate-limit window must be positive");
        }
        if (maxFailures < 0) {
            throw new IllegalArgumentException("Login rate-limit max failures must not be negative");
        }
        if (blockDuration.isZero() || blockDuration.isNegative()) {
            throw new IllegalArgumentException("Login rate-limit block duration must be positive");
        }
    }
}
