package com.jack.bytejudge.gateway.web;

import com.jack.bytejudge.common.core.response.ApiError;
import com.jack.bytejudge.common.core.response.ApiResponse;
import com.jack.bytejudge.common.web.RequestLogFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Gateway-owned error exit. Routing, load-balancing and proxying failures happen inside the
 * gateway's handler rather than a controller, so they share the frozen {@code code/data/message/
 * requestId/serverTime} contract here instead of falling through to a framework default page.
 */
@RestControllerAdvice
public class GatewayErrorAdvice {
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiResponse<Void>> notFound(HttpServletRequest request, Exception failure) {
        return respond(ApiError.NOT_FOUND, request, failure);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> methodNotAllowed(HttpServletRequest request, Exception failure) {
        return respond(ApiError.METHOD_NOT_ALLOWED, request, failure);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> unsupportedMediaType(HttpServletRequest request, Exception failure) {
        return respond(ApiError.UNSUPPORTED_MEDIA_TYPE, request, failure);
    }

    /** The load balancer reports an empty instance list with an HTTP-status exception. */
    @ExceptionHandler(HttpStatusCodeException.class)
    ResponseEntity<ApiResponse<Void>> upstreamStatus(HttpServletRequest request, HttpStatusCodeException failure) {
        return respond(ApiError.forStatus(failure.getStatusCode().value()), request, failure);
    }

    /** Connection refused, connect timeout or read timeout while proxying or verifying identity. */
    @ExceptionHandler({ResourceAccessException.class, DataAccessException.class})
    ResponseEntity<ApiResponse<Void>> dependencyUnavailable(HttpServletRequest request, Exception failure) {
        return respond(ApiError.SYSTEM_UNAVAILABLE, request, failure);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unknown(HttpServletRequest request, Exception failure) {
        return respond(ApiError.INTERNAL_ERROR, request, failure);
    }

    private ResponseEntity<ApiResponse<Void>> respond(ApiError error, HttpServletRequest request,
                                                      Exception failure) {
        // The response never carries the exception text, SQL, addresses or credentials; the log
        // keeps only the correlation id and exception type so both ends can be joined.
        LoggerFactory.getLogger("bytejudge.http").warn("gateway error requestId={} error={} type={}",
                RequestLogFilter.requestId(request), error.name(), failure.getClass().getName());
        return ResponseEntity.status(error.status())
                .body(ApiResponse.failure(error, RequestLogFilter.requestId(request)));
    }
}
