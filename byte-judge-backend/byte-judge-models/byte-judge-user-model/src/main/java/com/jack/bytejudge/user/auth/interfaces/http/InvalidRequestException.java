package com.jack.bytejudge.user.auth.interfaces.http;

/** A request that cannot be accepted at the protocol boundary, reported as HTTP 400. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException() {
        super("Invalid request.", null, false, false);
    }
}
