package com.jack.bytejudge.user.account.application;

import com.jack.bytejudge.user.account.application.port.PasswordHasher;
import com.jack.bytejudge.user.account.domain.repository.AdminAccountRepository;

import com.jack.bytejudge.user.account.domain.AccountRules;

public final class AdministratorCreator {
    private final AdminAccountRepository accounts;
    private final PasswordHasher passwords;

    public AdministratorCreator(AdminAccountRepository accounts, PasswordHasher passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    public String create(String account, char[] password, char[] confirmation) {
        String normalized = AccountRules.validateAdministrator(account, password, confirmation);
        accounts.createAdministrator(normalized, passwords.hash(password));
        return normalized;
    }
}
