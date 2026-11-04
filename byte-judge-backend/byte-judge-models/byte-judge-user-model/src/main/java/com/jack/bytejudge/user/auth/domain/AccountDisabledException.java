package com.jack.bytejudge.user.auth.domain;

/** The password already matched; only a non-login account state reaches this exception. */
public final class AccountDisabledException extends RuntimeException {
    public AccountDisabledException() {
        super("Account cannot sign in.");
    }
}
