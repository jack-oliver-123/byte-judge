package com.jack.bytejudge.user.auth.interfaces.internal;

import com.jack.bytejudge.user.auth.interfaces.http.InvalidRequestException;
import com.jack.bytejudge.user.auth.interfaces.http.StrictJson;

/** Internal protocol object, independent of the browser login body. */
public record IdentityVerificationRequest(String sessionId, String path) {

    public static IdentityVerificationRequest parse(byte[] body) {
        IdentityVerificationRequest request = StrictJson.read(body, IdentityVerificationRequest.class);
        if (request.sessionId() == null || request.sessionId().isBlank() || request.sessionId().length() > 256
                || request.path() == null || !request.path().startsWith("/")
                || request.path().length() > 256) {
            throw new InvalidRequestException();
        }
        return request;
    }
}
