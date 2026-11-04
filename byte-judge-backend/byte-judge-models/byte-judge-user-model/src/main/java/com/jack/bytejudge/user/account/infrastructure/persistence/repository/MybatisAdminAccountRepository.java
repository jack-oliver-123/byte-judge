package com.jack.bytejudge.user.account.infrastructure.persistence.repository;

import com.jack.bytejudge.user.account.domain.AccountConflictException;
import com.jack.bytejudge.user.account.domain.repository.AdminAccountRepository;
import com.jack.bytejudge.user.account.infrastructure.persistence.mapper.AdminAccountMapper;
import org.springframework.dao.DuplicateKeyException;

public final class MybatisAdminAccountRepository implements AdminAccountRepository {
    private final AdminAccountMapper mapper;

    public MybatisAdminAccountRepository(AdminAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void createAdministrator(String account, String passwordHash) {
        try {
            mapper.insertAdministrator(account, passwordHash);
        } catch (DuplicateKeyException exception) {
            throw new AccountConflictException();
        }
    }
}
