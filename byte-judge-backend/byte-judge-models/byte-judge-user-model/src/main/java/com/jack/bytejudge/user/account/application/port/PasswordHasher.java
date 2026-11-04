package com.jack.bytejudge.user.account.application.port;

@FunctionalInterface
public interface PasswordHasher {
    String hash(char[] password);
}
