package com.jack.bytejudge.user.auth.domain;

/** Wrong account or wrong password; the message never reveals which one failed. */
public final class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid account or password.");
    }
}
