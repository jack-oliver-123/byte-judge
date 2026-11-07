package com.jack.bytejudge.common.web;

import com.jack.bytejudge.common.core.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Shared framework policy, internal to the MVC and filter adapters in this module. */
final class CommonErrors {
    private static final Logger HTTP_LOG = LoggerFactory.getLogger("bytejudge.http");

    private CommonErrors() {
    }

    static ApiError classify(Throwable failure) {
        if (failure instanceof HttpMessageNotReadableException
                || failure instanceof MethodArgumentTypeMismatchException) {
            return ApiError.INVALID_REQUEST;
        }
        if (failure instanceof NoResourceFoundException || failure instanceof NoHandlerFoundException) {
            return ApiError.NOT_FOUND;
        }
        if (failure instanceof HttpRequestMethodNotSupportedException) {
            return ApiError.METHOD_NOT_ALLOWED;
        }
        if (failure instanceof HttpMediaTypeNotSupportedException) {
            return ApiError.UNSUPPORTED_MEDIA_TYPE;
        }
        return ApiError.INTERNAL_ERROR;
    }

    static void log(ApiError error, HttpServletRequest request, Throwable failure) {
        // Do not pass the Throwable to the logger: messages and stack traces may contain SQL,
        // internal addresses or credentials. The request id and type are enough to correlate exits.
        String requestId = RequestLogFilter.requestId(request);
        String type = failure.getClass().getName();
        if (error.status() >= 500) {
            HTTP_LOG.error("request failed requestId={} error={} type={}", requestId, error.name(), type);
        } else {
            HTTP_LOG.warn("request failed requestId={} error={} type={}", requestId, error.name(), type);
        }
    }
}
