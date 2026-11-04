package com.jack.bytejudge.user.auth.interfaces.http.vo;

/** Safe identity whitelist: user number as decimal string, normalized account, storage-decided role. */
public record IdentityView(String userId, String account, String role) {
    public static IdentityView of(long userId, String account, String role) {
        return new IdentityView(Long.toString(userId), account, role);
    }
}
