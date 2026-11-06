package com.jack.bytejudge.user.auth.application.port;

import com.jack.bytejudge.user.auth.domain.model.AccountSnapshot;

import java.util.Optional;

/** Reads the current account status, role and authentication epoch from authoritative storage. */
public interface AccountAuthority {
    Optional<AccountSnapshot> findById(long userId);
}
