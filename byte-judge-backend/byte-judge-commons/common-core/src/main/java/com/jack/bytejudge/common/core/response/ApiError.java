package com.jack.bytejudge.common.core.response;

public enum ApiError {
    INVALID_REQUEST(40000, 400, "Invalid request"),
    NOT_AUTHENTICATED(40100, 401, "Authentication required"),
    INVALID_CREDENTIALS(40101, 401, "Invalid account or password"),
    ACCESS_DENIED(40300, 403, "Access denied"),
    CSRF_INVALID(40301, 403, "Invalid or missing CSRF token"),
    ACCOUNT_DISABLED(40302, 403, "Account cannot sign in"),
    NOT_FOUND(40400, 404, "Resource not found"),
    METHOD_NOT_ALLOWED(40500, 405, "Method not allowed"),
    ALREADY_AUTHENTICATED(40900, 409, "Log out before signing in again"),
    UNSUPPORTED_MEDIA_TYPE(41500, 415, "Unsupported media type"),
    INTERNAL_ERROR(50000, 500, "Internal server error"),
    SYSTEM_UNAVAILABLE(50300, 503, "Service temporarily unavailable");

    private final int code;
    private final int status;
    private final String message;

    ApiError(int code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public int code() { return code; }
    public int status() { return status; }
    public String message() { return message; }
}
