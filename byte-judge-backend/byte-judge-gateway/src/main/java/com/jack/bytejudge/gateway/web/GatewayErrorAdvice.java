package com.jack.bytejudge.gateway.web;

import com.jack.bytejudge.common.core.response.ApiError;
import com.jack.bytejudge.common.core.response.ApiResponse;
import com.jack.bytejudge.common.web.CommonErrorAdvice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Gateway-owned error exit. Routing, load-balancing and proxying failures happen inside the
 * gateway's handler rather than a controller, so they share the frozen {@code code/data/message/
 * requestId/serverTime} contract here instead of falling through to a framework default page.
 */
@RestControllerAdvice
public class GatewayErrorAdvice extends CommonErrorAdvice {

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
}
