package com.jack.bytejudge.user.auth.interfaces.http.dto;

import com.jack.bytejudge.user.auth.interfaces.http.InvalidRequestException;
import com.jack.bytejudge.user.auth.interfaces.http.StrictJson;

/**
 * The login body is parsed by {@link StrictJson}, the one strict reader for authentication
 * protocol bodies: unknown or server-controlled fields, duplicate keys, scalar coercion and
 * trailing content are rejected, so a client can never smuggle a role or authentication version
 * into authentication.
 */
public record LoginRequest(String account, String password) {

    public static LoginRequest parse(byte[] body) {
        LoginRequest request = StrictJson.read(body, LoginRequest.class);
        if (request.account() == null || request.password() == null) {
            throw new InvalidRequestException();
        }
        return request;
    }
}
