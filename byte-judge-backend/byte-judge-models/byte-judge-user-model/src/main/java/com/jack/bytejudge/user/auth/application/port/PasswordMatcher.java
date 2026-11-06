package com.jack.bytejudge.user.auth.application.port;

@FunctionalInterface
public interface PasswordMatcher {
    boolean matches(char[] rawPassword, String storedHash);
}
