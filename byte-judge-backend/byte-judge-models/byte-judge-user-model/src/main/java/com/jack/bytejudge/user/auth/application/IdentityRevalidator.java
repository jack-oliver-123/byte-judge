package com.jack.bytejudge.user.auth.application;

import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.user.auth.application.port.AccountAuthority;
import com.jack.bytejudge.user.auth.application.port.SessionRevocation;
import com.jack.bytejudge.user.auth.domain.SessionRejectedException;
import com.jack.bytejudge.user.auth.domain.model.AccountSnapshot;

import java.util.Optional;

/**
 * Rechecks the session snapshot against the account's current status, role and authentication
 * epoch before protected work runs, and refuses identifiers that were durably revoked. There is
 * deliberately no positive cache: the user service is the only authority, so a changed status or
 * epoch takes effect on the next protected request.
 */
public final class IdentityRevalidator {
    private final AccountAuthority accounts;
    private final SessionRevocation revocations;

    public IdentityRevalidator(AccountAuthority accounts, SessionRevocation revocations) {
        this.accounts = accounts;
        this.revocations = revocations;
    }

    public AccountSnapshot revalidate(String sessionId, SessionIdentity identity) {
        if (revocations.isRevoked(sessionId)) {
            throw SessionRejectedException.revoked();
        }
        Optional<AccountSnapshot> found = accounts.findById(identity.userId());
        if (found.isEmpty()) {
            return reject(sessionId);
        }
        AccountSnapshot current = found.get();
        if (!current.account().equals(identity.account())
                || !current.role().storedName().equals(identity.role())
                || !current.status().loginAllowed()
                || current.authEpoch() != identity.authEpoch()) {
            return reject(sessionId);
        }
        return current;
    }

    /**
     * The durable tombstone is committed before the rejection is reported, so restoring the account
     * afterwards cannot make this session valid again. If the write fails, the storage exception
     * propagates and the caller fails the request closed.
     */
    private AccountSnapshot reject(String sessionId) {
        revocations.revoke(sessionId);
        throw new SessionRejectedException();
    }
}
