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
    LOGIN_RATE_LIMITED(42900, 429, "Too many login attempts"),
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

    /**
     * Maps an HTTP status produced outside MVC's typed exceptions onto the frozen error contract.
     * Dependency and unavailable states collapse to 503 so a gateway or storage fault is never
     * reported as a business success or as an unrelated 500.
     */
    public static ApiError forStatus(int status) {
        return switch (status) {
            case 400 -> INVALID_REQUEST;
            case 401 -> NOT_AUTHENTICATED;
            case 403 -> ACCESS_DENIED;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> ALREADY_AUTHENTICATED;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> LOGIN_RATE_LIMITED;
            case 502, 503, 504 -> SYSTEM_UNAVAILABLE;
            default -> status >= 500 ? INTERNAL_ERROR : INVALID_REQUEST;
        };
    }
}
