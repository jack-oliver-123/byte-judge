package com.jack.bytejudge.common.web;

import com.jack.bytejudge.common.core.response.ApiError;
import com.jack.bytejudge.common.core.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/** Filter-level security exits use the same JSON contract as MVC errors. */
public final class SecurityErrorWriter {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SecurityErrorWriter() { }

    public static AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, failure) -> write(request, response, ApiError.NOT_AUTHENTICATED);
    }

    public static AccessDeniedHandler accessDeniedHandler() {
        return (request, response, failure) -> write(request, response,
                failure instanceof CsrfException ? ApiError.CSRF_INVALID : ApiError.ACCESS_DENIED);
    }

    /** Shared error exit for filters that reject before MVC or Security's own handlers run. */
    public static void writeError(HttpServletRequest request, HttpServletResponse response, ApiError error)
            throws IOException {
        write(request, response, error);
    }

    private static void write(HttpServletRequest request, HttpServletResponse response, ApiError error)
            throws IOException {
        response.setStatus(error.status());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.writeValueAsString(
                ApiResponse.failure(error, RequestLogFilter.requestId(request))));
    }
}
