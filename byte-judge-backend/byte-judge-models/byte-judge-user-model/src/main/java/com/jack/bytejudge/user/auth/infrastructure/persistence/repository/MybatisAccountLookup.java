package com.jack.bytejudge.user.auth.infrastructure.persistence.repository;

import com.jack.bytejudge.user.account.domain.AccountRole;
import com.jack.bytejudge.user.account.domain.AccountStatus;
import com.jack.bytejudge.user.auth.application.port.AccountLookup;
import com.jack.bytejudge.user.auth.domain.model.StoredAccount;
import com.jack.bytejudge.user.auth.infrastructure.persistence.mapper.UserAccountMapper;

import java.util.Optional;

public final class MybatisAccountLookup implements AccountLookup {
    private final UserAccountMapper mapper;

    public MybatisAccountLookup(UserAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<StoredAccount> findByNormalizedAccount(String account) {
        return Optional.ofNullable(mapper.findAccount(account))
                .map(row -> new StoredAccount(row.id(), row.account(), row.passwordHash(),
                        AccountRole.fromStored(row.role()), AccountStatus.fromStored(row.status()),
                        row.authEpoch()));
    }
}
