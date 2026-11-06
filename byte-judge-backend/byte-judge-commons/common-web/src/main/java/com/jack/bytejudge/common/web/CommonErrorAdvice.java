package com.jack.bytejudge.common.web;

import com.jack.bytejudge.common.core.response.ApiError;
import com.jack.bytejudge.common.core.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Common MVC error exit, explicitly installed by a service's {@code @RestControllerAdvice}
 * subclass. Service-owned, more specific handlers take precedence over this fallback in the same
 * advice; no component scanning or ordering between separate advice beans is required.
 *
 * <p>Only generic framework errors belong here. Authentication, rate limits, storage policy and
 * upstream HTTP statuses stay with their owning adapters. The response uses frozen error codes and
 * fixed messages, and logs never include the exception message or stack trace.
 */
public abstract class CommonErrorAdvice {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> frameworkFailure(HttpServletRequest request, Exception failure) {
        return respond(CommonErrors.classify(failure), request, failure);
    }

    protected final ResponseEntity<ApiResponse<Void>> respond(ApiError error, HttpServletRequest request) {
        return ResponseEntity.status(error.status())
                .body(ApiResponse.failure(error, RequestLogFilter.requestId(request)));
    }

    protected final ResponseEntity<ApiResponse<Void>> respond(ApiError error, HttpServletRequest request,
                                                              Exception failure) {
        CommonErrors.log(error, request, failure);
        return respond(error, request);
    }
}
