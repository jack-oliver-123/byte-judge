package com.jack.bytejudge.user.auth.domain.model;

/** Account facts owned by the user service; the hash never leaves the persistence boundary. */
public record StoredAccount(long userId, String account, String passwordHash, String role, String status) {
    public boolean loginAllowed() {
        return "normal".equals(status);
    }
}
