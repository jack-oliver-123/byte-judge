package com.jack.bytejudge.user.auth.application.port;

import java.time.Duration;
import java.util.Optional;

/**
 * Temporary, Redis-backed accounting for failed login attempts. Each dimension (normalized
 * account and trusted source address) keeps its own failure count and block; a login is denied
 * while either dimension is blocked.
 */
public interface LoginAttemptLimiter {
    /**
     * @return how long the caller must wait when this account/source pair is currently blocked,
     * or empty when a login attempt may proceed.
     */
    Optional<Duration> blockedFor(String normalizedAccount, String sourceAddress);

    /**
     * Records one credential failure and arms the 15-minute block once a dimension reaches its
     * threshold.
     *
     * @return the remaining block when this failure itself is beyond the threshold (the caller
     * must answer 429), or empty for an ordinary failure response.
     */
    Optional<Duration> recordFailure(String normalizedAccount, String sourceAddress);
}
