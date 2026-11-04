package com.jack.bytejudge.common.core.security;

import java.io.Serializable;
import java.security.Principal;

/**
 * Minimal authenticated identity shared by the gateway and the owning service through the Redis
 * session. It carries no framework, entity, contact or credential data, so both processes can
 * deserialize it with the same contract class.
 */
public record SessionIdentity(long userId, String account, String role) implements Principal, Serializable {
    public static final String ROLE_PREFIX = "ROLE_";

    @Override
    public String getName() {
        return account;
    }
}
