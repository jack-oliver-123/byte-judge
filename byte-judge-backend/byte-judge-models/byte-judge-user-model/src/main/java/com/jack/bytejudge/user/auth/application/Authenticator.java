package com.jack.bytejudge.user.auth.application;

import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.user.account.domain.AccountRules;
import com.jack.bytejudge.user.auth.application.port.AccountLookup;
import com.jack.bytejudge.user.auth.application.port.PasswordMatcher;
import com.jack.bytejudge.user.auth.domain.AccountDisabledException;
import com.jack.bytejudge.user.auth.domain.InvalidCredentialsException;
import com.jack.bytejudge.user.auth.domain.model.StoredAccount;

public final class Authenticator {
    private final AccountLookup accounts;
    private final PasswordMatcher passwords;

    public Authenticator(AccountLookup accounts, PasswordMatcher passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    public SessionIdentity authenticate(String account, char[] password) {
        // Rule violations surface as account input errors (HTTP 400); only a well-formed but wrong
        // account/password pair becomes a credential error.
        String normalized = AccountRules.validateLogin(account, password);
        StoredAccount stored = accounts.findByNormalizedAccount(normalized)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwords.matches(password, stored.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!stored.loginAllowed()) {
            throw new AccountDisabledException();
        }
        return new SessionIdentity(stored.userId(), stored.account(), stored.role());
    }
}
