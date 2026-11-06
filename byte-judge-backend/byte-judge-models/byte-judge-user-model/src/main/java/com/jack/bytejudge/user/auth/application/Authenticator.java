package com.jack.bytejudge.user.auth.application;

import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.user.account.domain.AccountRules;
import com.jack.bytejudge.user.auth.application.port.AccountLookup;
import com.jack.bytejudge.user.auth.application.port.AuthAudit;
import com.jack.bytejudge.user.auth.application.port.LoginAttemptLimiter;
import com.jack.bytejudge.user.auth.application.port.PasswordMatcher;
import com.jack.bytejudge.user.auth.domain.AccountDisabledException;
import com.jack.bytejudge.user.auth.domain.InvalidCredentialsException;
import com.jack.bytejudge.user.auth.domain.LoginRateLimitedException;
import com.jack.bytejudge.user.auth.domain.model.AuthAuditEntry;
import com.jack.bytejudge.user.auth.domain.model.AuthEvent;
import com.jack.bytejudge.user.auth.domain.model.AuthOutcome;
import com.jack.bytejudge.user.auth.domain.model.StoredAccount;

import java.time.Duration;
import java.util.Optional;

public final class Authenticator {
    private final AccountLookup accounts;
    private final PasswordMatcher passwords;
    private final AuthAudit audit;
    private final LoginAttemptLimiter loginAttempts;

    public Authenticator(AccountLookup accounts, PasswordMatcher passwords, AuthAudit audit,
                         LoginAttemptLimiter loginAttempts) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.audit = audit;
        this.loginAttempts = loginAttempts;
    }

    public SessionIdentity authenticate(String account, char[] password, String requestId, String sourceAddress) {
        // Rule violations surface as account input errors (HTTP 400); only a well-formed but wrong
        // account/password pair becomes a credential error or a rate-limit event.
        String normalized = AccountRules.validateLogin(account, password);
        loginAttempts.blockedFor(normalized, sourceAddress).ifPresent(retryAfter -> {
            reject(normalized, null, "RATE_LIMITED", requestId);
            throw new LoginRateLimitedException(retryAfter);
        });
        StoredAccount stored = accounts.findByNormalizedAccount(normalized).orElse(null);
        if (stored == null || !passwords.matches(password, stored.passwordHash())) {
            reject(normalized, null, "CREDENTIALS", requestId);
            // Unknown accounts and wrong passwords share this counter path, so neither the
            // response nor the counters can distinguish them.
            Optional<Duration> retryAfter = loginAttempts.recordFailure(normalized, sourceAddress);
            if (retryAfter.isPresent()) {
                throw new LoginRateLimitedException(retryAfter.get());
            }
            throw new InvalidCredentialsException();
        }
        if (!stored.loginAllowed()) {
            reject(normalized, stored.userId(), "ACCOUNT_DISABLED", requestId);
            throw new AccountDisabledException();
        }
        audit.record(new AuthAuditEntry(AuthEvent.LOGIN, AuthOutcome.SUCCESS, stored.userId(),
                stored.account(), null, requestId));
        return new SessionIdentity(stored.userId(), stored.account(), stored.role().storedName(),
                stored.authEpoch());
    }

    private void reject(String account, Long userId, String reason, String requestId) {
        // The audit row never contains the attempted password, its hash or the session identifier.
        audit.record(new AuthAuditEntry(AuthEvent.LOGIN, AuthOutcome.REJECTED, userId, account,
                reason, requestId));
    }
}
