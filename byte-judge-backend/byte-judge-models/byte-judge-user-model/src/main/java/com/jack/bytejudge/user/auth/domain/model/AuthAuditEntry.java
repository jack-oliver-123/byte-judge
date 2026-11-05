package com.jack.bytejudge.user.auth.domain.model;

/**
 * One authentication/permission audit fact. It deliberately carries no password, password hash,
 * session identifier, CSRF value or internal service credential; {@code reason} is a fixed
 * classification chosen by this service, never client text.
 */
public record AuthAuditEntry(AuthEvent event, AuthOutcome outcome, Long userId, String account,
                             String reason, String requestId) {
}
