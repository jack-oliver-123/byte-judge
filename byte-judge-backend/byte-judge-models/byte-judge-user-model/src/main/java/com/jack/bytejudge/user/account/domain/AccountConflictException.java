package com.jack.bytejudge.user.account.domain;

public final class AccountConflictException extends RuntimeException {
    public AccountConflictException() {
        super("Account already exists; no account was changed.");
    }
}
