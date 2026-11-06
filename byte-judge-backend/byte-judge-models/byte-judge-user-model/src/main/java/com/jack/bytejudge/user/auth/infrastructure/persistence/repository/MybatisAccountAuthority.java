package com.jack.bytejudge.user.auth.infrastructure.persistence.repository;

import com.jack.bytejudge.user.account.domain.AccountRole;
import com.jack.bytejudge.user.account.domain.AccountStatus;
import com.jack.bytejudge.user.auth.application.port.AccountAuthority;
import com.jack.bytejudge.user.auth.domain.model.AccountSnapshot;
import com.jack.bytejudge.user.auth.infrastructure.persistence.mapper.UserAccountMapper;

import java.util.Optional;

public final class MybatisAccountAuthority implements AccountAuthority {
    private final UserAccountMapper mapper;

    public MybatisAccountAuthority(UserAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AccountSnapshot> findById(long userId) {
        return Optional.ofNullable(mapper.findAccountSnapshot(userId))
                .map(row -> new AccountSnapshot(row.id(), row.account(),
                        AccountRole.fromStored(row.role()), AccountStatus.fromStored(row.status()),
                        row.authEpoch()));
    }
}
