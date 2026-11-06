package com.jack.bytejudge.user.auth.domain.model;

import com.jack.bytejudge.user.account.domain.AccountRole;
import com.jack.bytejudge.user.account.domain.AccountStatus;

/** Current account facts read from the user service's authoritative storage. */
public record AccountSnapshot(long userId, String account, AccountRole role, AccountStatus status,
                              long authEpoch) {
}
