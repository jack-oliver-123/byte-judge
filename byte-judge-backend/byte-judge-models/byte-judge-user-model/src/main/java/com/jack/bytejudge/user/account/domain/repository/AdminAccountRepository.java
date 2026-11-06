package com.jack.bytejudge.user.account.domain.repository;

public interface AdminAccountRepository {
    void createAdministrator(String account, String passwordHash);
}
