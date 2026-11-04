package com.jack.bytejudge.user.auth.domain.model;

/** Authentication and permission events persisted by the owning user service. */
public enum AuthEvent {
    LOGIN,
    LOGOUT,
    IDENTITY_CHECK,
    ADMIN_ACCESS,
    INTERNAL_VERIFY
}
