package com.jack.bytejudge.common.web;

import com.jack.bytejudge.common.core.response.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

/**
 * Last-resort error exit for failures that escape the filter chain before MVC can handle them:
 * shared storage that cannot load or save, or a gateway proxy/load-balancing failure. It writes the
 * same JSON contract as controller and Security errors, discards a buffered partial success body,
 * and never exposes the exception text.
 *
 * <p>Raw I/O failures are rethrown because the client is usually gone; reporting them would only
 * risk a second failure while writing. Everything else, including unknown exceptions, becomes a
 * contract response so no caller can mistake a fault for a business result.
 */
public final class ContractErrorFilter extends OncePerRequestFilter {
    private static final Logger HTTP_LOG = LoggerFactory.getLogger("bytejudge.http");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (Exception failure) {
            if (response.isCommitted()) {
                throw failure;
            }
            ApiError error = classify(failure);
            if (error == null) {
                if (failure instanceof ServletException servletFailure) {
                    throw servletFailure;
                }
                if (failure instanceof IOException ioFailure) {
                    throw ioFailure;
                }
                if (failure instanceof RuntimeException runtimeFailure) {
                    throw runtimeFailure;
                }
                throw new ServletException(failure);
            }
            HTTP_LOG.warn("request failed requestId={} type={}",
                    RequestLogFilter.requestId(request), rootCause(failure).getClass().getName());
            write(request, response, error);
        }
    }

    /** Returns the contract error for this failure, or null when no response should be written. */
    static ApiError classify(Throwable failure) {
        Throwable cause = rootCause(failure);
        if (cause instanceof HttpStatusCodeException statusFailure) {
            return ApiError.forStatus(statusFailure.getStatusCode().value());
        }
        if (cause instanceof DataAccessException || cause instanceof ResourceAccessException) {
            // Session/rate-limit storage and unreachable downstreams are dependency failures, not
            // this service's internal error.
            return ApiError.SYSTEM_UNAVAILABLE;
        }
        if (cause instanceof AuthenticationException) {
            return ApiError.NOT_AUTHENTICATED;
        }
        if (cause instanceof AccessDeniedException) {
            return ApiError.ACCESS_DENIED;
        }
        if (cause instanceof HttpRequestMethodNotSupportedException) {
            return ApiError.METHOD_NOT_ALLOWED;
        }
        if (cause instanceof HttpMediaTypeNotSupportedException) {
            return ApiError.UNSUPPORTED_MEDIA_TYPE;
        }
        if (cause instanceof NoResourceFoundException || cause instanceof NoHandlerFoundException) {
            return ApiError.NOT_FOUND;
        }
        if (cause instanceof IOException) {
            return null;
        }
        return ApiError.INTERNAL_ERROR;
    }

    private static void write(HttpServletRequest request, HttpServletResponse response, ApiError error)
            throws IOException {
        // A controller may already have buffered a success body before the session commit failed.
        // Drop it, then restore the correlation headers that belong to the original request.
        response.reset();
        response.setHeader(RequestLogFilter.HEADER, RequestLogFilter.requestId(request));
        response.setHeader("Cache-Control", "no-store");
        SecurityErrorWriter.writeError(request, response, error);
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof ServletException servletFailure
                && servletFailure.getRootCause() != null
                && servletFailure.getRootCause() != cause) {
            cause = servletFailure.getRootCause();
        }
        return cause;
    }
}
