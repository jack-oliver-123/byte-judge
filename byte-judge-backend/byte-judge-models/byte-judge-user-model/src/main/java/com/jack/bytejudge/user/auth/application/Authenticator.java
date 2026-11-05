package com.jack.bytejudge.user.auth.application;

import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.user.account.domain.AccountRules;
import com.jack.bytejudge.user.auth.application.port.AccountLookup;
import com.jack.bytejudge.user.auth.application.port.AuthAudit;
import com.jack.bytejudge.user.auth.application.port.PasswordMatcher;
import com.jack.bytejudge.user.auth.domain.AccountDisabledException;
import com.jack.bytejudge.user.auth.domain.InvalidCredentialsException;
import com.jack.bytejudge.user.auth.domain.model.AuthAuditEntry;
import com.jack.bytejudge.user.auth.domain.model.AuthEvent;
import com.jack.bytejudge.user.auth.domain.model.AuthOutcome;
import com.jack.bytejudge.user.auth.domain.model.StoredAccount;

public final class Authenticator {
    private final AccountLookup accounts;
    private final PasswordMatcher passwords;
    private final AuthAudit audit;

    public Authenticator(AccountLookup accounts, PasswordMatcher passwords, AuthAudit audit) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.audit = audit;
    }

    public SessionIdentity authenticate(String account, char[] password, String requestId) {
        // Rule violations surface as account input errors (HTTP 400); only a well-formed but wrong
        // account/password pair becomes a credential error.
        String normalized = AccountRules.validateLogin(account, password);
        StoredAccount stored = accounts.findByNormalizedAccount(normalized).orElse(null);
        if (stored == null || !passwords.matches(password, stored.passwordHash())) {
            reject(normalized, null, "CREDENTIALS", requestId);
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
