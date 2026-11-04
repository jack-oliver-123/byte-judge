package com.jack.bytejudge.user.config;

import com.jack.bytejudge.common.core.response.ApiError;
import com.jack.bytejudge.common.core.response.ApiResponse;
import com.jack.bytejudge.common.web.RequestLogFilter;
import com.jack.bytejudge.user.account.domain.AccountInputException;
import com.jack.bytejudge.user.auth.domain.AlreadyAuthenticatedException;
import com.jack.bytejudge.user.auth.domain.NotAuthenticatedException;
import com.jack.bytejudge.user.auth.interfaces.http.InvalidRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class WebErrorAdvice {
    @ExceptionHandler(AlreadyAuthenticatedException.class)
    ResponseEntity<ApiResponse<Void>> alreadyAuthenticated(HttpServletRequest request) {
        return respond(ApiError.ALREADY_AUTHENTICATED, request);
    }

    @ExceptionHandler(NotAuthenticatedException.class)
    ResponseEntity<ApiResponse<Void>> notAuthenticated(HttpServletRequest request) {
        return respond(ApiError.NOT_AUTHENTICATED, request);
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

    @ExceptionHandler(AccountInputException.class)
    ResponseEntity<ApiResponse<Void>> accountInput(HttpServletRequest request) {
        return respond(ApiError.INVALID_REQUEST, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            InvalidRequestException.class})
    ResponseEntity<ApiResponse<Void>> invalidInput(HttpServletRequest request) {
        return respond(ApiError.INVALID_REQUEST, request);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ApiResponse<Void>> notFound(HttpServletRequest request) {
        return respond(ApiError.NOT_FOUND, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> methodNotAllowed(HttpServletRequest request) {
        return respond(ApiError.METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> unsupportedMediaType(HttpServletRequest request) {
        return respond(ApiError.UNSUPPORTED_MEDIA_TYPE, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unknown(HttpServletRequest request, Exception failure) {
        // The client sees a fixed message; the server log records only the correlation id and the
        // exception type, never the request body, SQL or connection details.
        LoggerFactory.getLogger("bytejudge.http").error("unhandled requestId={} type={}",
                RequestLogFilter.requestId(request), failure.getClass().getName());
        return respond(ApiError.INTERNAL_ERROR, request);
    }

    private ResponseEntity<ApiResponse<Void>> respond(ApiError error, HttpServletRequest request) {
        return ResponseEntity.status(error.status())
                .body(ApiResponse.failure(error, RequestLogFilter.requestId(request)));
    }
}
