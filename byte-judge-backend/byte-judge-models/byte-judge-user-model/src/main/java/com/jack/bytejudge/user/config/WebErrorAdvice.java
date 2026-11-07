package com.jack.bytejudge.user.config;

import com.jack.bytejudge.common.core.response.ApiError;
import com.jack.bytejudge.common.core.response.ApiResponse;
import com.jack.bytejudge.common.web.CommonErrorAdvice;
import com.jack.bytejudge.common.web.RequestLogFilter;
import com.jack.bytejudge.user.account.domain.AccountInputException;
import com.jack.bytejudge.user.auth.domain.AlreadyAuthenticatedException;
import com.jack.bytejudge.user.auth.domain.LoginRateLimitedException;
import com.jack.bytejudge.user.auth.domain.NotAuthenticatedException;
import com.jack.bytejudge.user.auth.domain.PermissionDeniedException;
import com.jack.bytejudge.user.auth.domain.SessionRejectedException;
import com.jack.bytejudge.user.auth.interfaces.http.InvalidRequestException;
import com.jack.bytejudge.user.auth.interfaces.http.vo.RetryAfterView;
import com.jack.bytejudge.user.auth.interfaces.internal.InternalAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** User-owned error policies; framework failures are handled by the common MVC adapter. */
@RestControllerAdvice
public class WebErrorAdvice extends CommonErrorAdvice {
    @ExceptionHandler(AlreadyAuthenticatedException.class)
    ResponseEntity<ApiResponse<Void>> alreadyAuthenticated(HttpServletRequest request) {
        return respond(ApiError.ALREADY_AUTHENTICATED, request);
    }

    @ExceptionHandler(NotAuthenticatedException.class)
    ResponseEntity<ApiResponse<Void>> notAuthenticated(HttpServletRequest request) {
        return respond(ApiError.NOT_AUTHENTICATED, request);
    }

    /** The session points at an account whose current status, role or epoch no longer matches. */
    @ExceptionHandler(SessionRejectedException.class)
    ResponseEntity<ApiResponse<Void>> sessionRejected(HttpServletRequest request) {
        return respond(ApiError.NOT_AUTHENTICATED, request);
    }

    /** Either the normalized account or the trusted source address is temporarily blocked. */
    @ExceptionHandler(LoginRateLimitedException.class)
    ResponseEntity<ApiResponse<RetryAfterView>> loginRateLimited(HttpServletRequest request,
                                                                 LoginRateLimitedException limited) {
        long retryAfterSeconds = Math.max(1, (limited.retryAfter().toMillis() + 999) / 1000);
        return ResponseEntity.status(ApiError.LOGIN_RATE_LIMITED.status())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(ApiResponse.failure(ApiError.LOGIN_RATE_LIMITED,
                        new RetryAfterView(retryAfterSeconds), RequestLogFilter.requestId(request)));
    }

    @ExceptionHandler(InternalAccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> internalAccessDenied(HttpServletRequest request) {
        return respond(ApiError.ACCESS_DENIED, request);
    }

    @ExceptionHandler(PermissionDeniedException.class)
    ResponseEntity<ApiResponse<Void>> permissionDenied(HttpServletRequest request) {
        return respond(ApiError.ACCESS_DENIED, request);
    }

    /** A required authoritative lookup could not complete, so protected work was not executed. */
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiResponse<Void>> storageUnavailable(HttpServletRequest request, DataAccessException failure) {
        return respond(ApiError.SYSTEM_UNAVAILABLE, request, failure);
    }

    /** Authentication failures raised by Spring Security's AuthenticationManager. */
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiResponse<Void>> badCredentials(HttpServletRequest request) {
        return respond(ApiError.INVALID_CREDENTIALS, request);
    }

    @ExceptionHandler(DisabledException.class)
    ResponseEntity<ApiResponse<Void>> disabledAccount(HttpServletRequest request) {
        return respond(ApiError.ACCOUNT_DISABLED, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiResponse<Void>> authenticationFailure(HttpServletRequest request) {
        return respond(ApiError.NOT_AUTHENTICATED, request);
    }

    @ExceptionHandler({AccountInputException.class, InvalidRequestException.class})
    ResponseEntity<ApiResponse<Void>> invalidInput(HttpServletRequest request) {
        return respond(ApiError.INVALID_REQUEST, request);
    }
}
