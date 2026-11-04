package com.jack.bytejudge.user.auth.interfaces.internal;

import com.jack.bytejudge.user.auth.domain.model.AccountSnapshot;

/** Whitelist of facts the gateway may use for its entry check. */
public record IdentityVerificationView(String userId, String account, String role) {
    static IdentityVerificationView of(AccountSnapshot account) {
        return new IdentityVerificationView(Long.toString(account.userId()), account.account(),
                account.role().storedName());
    }
}
