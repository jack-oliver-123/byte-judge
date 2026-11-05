package com.jack.bytejudge.user.auth.domain.model;

import com.jack.bytejudge.user.account.domain.AccountRole;
import com.jack.bytejudge.user.account.domain.AccountStatus;

/** Account facts owned by the user service; the hash never leaves the persistence boundary. */
public record StoredAccount(long userId, String account, String passwordHash,
                            AccountRole role, AccountStatus status, long authEpoch) {
    public boolean loginAllowed() {
        return status.loginAllowed();
    }
}
