package com.jack.bytejudge.user.auth.application;

import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.user.auth.application.port.AccountAuthority;
import com.jack.bytejudge.user.auth.domain.SessionRejectedException;
import com.jack.bytejudge.user.auth.domain.model.AccountSnapshot;

/**
 * Rechecks the session snapshot against the account's current status, role and authentication
 * epoch before protected work runs. There is deliberately no positive cache: the user service is
 * the only authority, so a changed status or epoch takes effect on the next protected request.
 */
public final class IdentityRevalidator {
    private final AccountAuthority accounts;

    public IdentityRevalidator(AccountAuthority accounts) {
        this.accounts = accounts;
    }

    public AccountSnapshot revalidate(SessionIdentity identity) {
        AccountSnapshot current = accounts.findById(identity.userId())
                .orElseThrow(SessionRejectedException::new);
        if (!current.account().equals(identity.account())
                || !current.role().storedName().equals(identity.role())
                || !current.status().loginAllowed()
                || current.authEpoch() != identity.authEpoch()) {
            throw new SessionRejectedException();
        }
        return current;
    }
}
