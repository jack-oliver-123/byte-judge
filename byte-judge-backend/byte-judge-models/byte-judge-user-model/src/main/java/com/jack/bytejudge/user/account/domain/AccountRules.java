package com.jack.bytejudge.user.account.domain;

import java.util.Arrays;
import java.util.Locale;

public final class AccountRules {
    private AccountRules() { }

    public static String validateAdministrator(String account, char[] password, char[] confirmation) {
        String normalized = validateAccount(account);
        if (password == null || confirmation == null) {
            throw new AccountInputException("Password and confirmation are required.");
        }
        validatePasswordCodePoints(password);
        if (!Arrays.equals(password, confirmation)) {
            throw new AccountInputException("Password confirmation must match.");
        }
        return normalized;
    }

    public static String validateLogin(String account, char[] password) {
        String normalized = validateAccount(account);
        if (password == null) {
            throw new AccountInputException("Password is required.");
        }
        validatePasswordCodePoints(password);
        return normalized;
    }

    private static String validateAccount(String account) {
        if (account == null || !account.strip().matches("[A-Za-z0-9_]{4,32}")) {
            throw new AccountInputException("Account must contain 4-32 ASCII letters, digits or underscores.");
        }
        return account.strip().toLowerCase(Locale.ROOT);
    }

    private static void validatePasswordCodePoints(char[] password) {
        int codePoints = Character.codePointCount(password, 0, password.length);
        if (codePoints < 8 || codePoints > 64) {
            throw new AccountInputException("Password must contain 8-64 Unicode code points.");
        }
    }
}
