package com.jack.bytejudge.common.web;

import com.jack.bytejudge.common.core.response.ApiError;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractErrorFilterTest {
    private static final String SENSITIVE = "private-password-jdbc:secret-address";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    static Stream<Object[]> failures() {
        return Stream.of(
                new Object[]{new ServletException(new ServletException(
                        new HttpRequestMethodNotSupportedException("POST"))), ApiError.METHOD_NOT_ALLOWED},
                new Object[]{new ServletException(new DataAccessResourceFailureException(SENSITIVE)),
                        ApiError.SYSTEM_UNAVAILABLE},
                // Do not unwrap arbitrary causes: a transport error with an IOException cause
                // still means an unavailable dependency, rather than a disconnected caller.
                new Object[]{new ResourceAccessException(SENSITIVE, new IOException(SENSITIVE)),
                        ApiError.SYSTEM_UNAVAILABLE},
                new Object[]{HttpServerErrorException.create(HttpStatus.GATEWAY_TIMEOUT, SENSITIVE,
                        HttpHeaders.EMPTY, SENSITIVE.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8),
                        ApiError.SYSTEM_UNAVAILABLE},
                new Object[]{new AuthenticationCredentialsNotFoundException(SENSITIVE), ApiError.NOT_AUTHENTICATED},
                new Object[]{new AccessDeniedException(SENSITIVE), ApiError.ACCESS_DENIED},
                new Object[]{new IllegalStateException(SENSITIVE), ApiError.INTERNAL_ERROR});
    }

    @ParameterizedTest
    @MethodSource("failures")
    void replacesBufferedSuccessWithAContractError(Exception failure, ApiError expected) throws Exception {
        var request = new MockHttpServletRequest("GET", "/probe");
        request.addHeader(RequestLogFilter.HEADER, "filter-contract");
        var response = new MockHttpServletResponse();
        new RequestLogFilter(false).doFilter(request, response, (correlatedRequest, correlatedResponse) ->
                new ContractErrorFilter().doFilter(correlatedRequest, correlatedResponse, (req, res) -> {
                    response.setHeader("Set-Cookie", "partial-session=" + SENSITIVE);
                    response.getWriter().write("partial-success-" + SENSITIVE);
                    throwFromChain(failure);
                }));
        var body = JSON.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(expected.status());
        assertThat(body.path("code").asInt()).isEqualTo(expected.code());
        assertThat(body.path("message").asText()).isEqualTo(expected.message());
        assertThat(body.path("data").isNull()).isTrue();
        assertThat(body.path("requestId").asText()).isEqualTo("filter-contract");
        assertThat(response.getHeader(RequestLogFilter.HEADER)).isEqualTo("filter-contract");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(response.getContentAsString()).doesNotContain(SENSITIVE, "partial-success");
    }

    static Stream<Exception> disconnectedClients() {
        return Stream.of(new IOException(SENSITIVE), new ServletException(new IOException(SENSITIVE)));
    }

    @ParameterizedTest
    @MethodSource("disconnectedClients")
    void rethrowsIoFailuresWithoutWritingAResponse(Exception failure) {
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> new ContractErrorFilter().doFilter(new MockHttpServletRequest(), response,
                (req, res) -> throwFromChain(failure))).isSameAs(failure);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentType()).isNull();
    }

    @Test
    void rethrowsAfterCommitWithoutReplacingTheOriginalResponse() throws Exception {
        var response = new MockHttpServletResponse();
        response.setStatus(202);
        response.getWriter().write("committed-result");
        response.flushBuffer();
        var failure = new IllegalStateException(SENSITIVE);
        assertThatThrownBy(() -> new ContractErrorFilter().doFilter(new MockHttpServletRequest(), response,
                (req, res) -> { throw failure; })).isSameAs(failure);
        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(response.getContentAsString()).isEqualTo("committed-result");
    }

    private static void throwFromChain(Exception failure) throws ServletException, IOException {
        if (failure instanceof ServletException servletFailure) {
            throw servletFailure;
        }
        if (failure instanceof IOException ioFailure) {
            throw ioFailure;
        }
        throw (RuntimeException) failure;
    }
}
