package com.jack.bytejudge.user.auth.application.port;

import com.jack.bytejudge.user.auth.domain.model.StoredAccount;

import java.util.Optional;

public interface AccountLookup {
    Optional<StoredAccount> findByNormalizedAccount(String account);
}
