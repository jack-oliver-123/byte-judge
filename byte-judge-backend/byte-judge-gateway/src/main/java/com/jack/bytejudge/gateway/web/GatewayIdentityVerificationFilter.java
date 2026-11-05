package com.jack.bytejudge.gateway.web;

import com.jack.bytejudge.common.core.response.ApiError;
import com.jack.bytejudge.common.web.ProtectedPaths;
import com.jack.bytejudge.common.web.RequestLogFilter;
import com.jack.bytejudge.common.web.SecurityErrorWriter;
import com.jack.bytejudge.gateway.identity.IdentityVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Gateway entry check. Protected browser paths need a valid session that the user service confirms
 * against authoritative account state; administrative paths additionally require the admin role.
 * The gateway does not read or modify the session, and it refuses internal paths outright.
 */
public final class GatewayIdentityVerificationFilter extends OncePerRequestFilter {
    private static final String SESSION_COOKIE = "BJSESSION";

    private final IdentityVerifier verifier;

    public GatewayIdentityVerificationFilter(IdentityVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = ProtectedPaths.routingPath(request);
        if (ProtectedPaths.isInternal(path)) {
            SecurityErrorWriter.writeError(request, response, ApiError.NOT_FOUND);
            return;
        }
        if (!ProtectedPaths.isProtected(path)) {
            chain.doFilter(request, response);
            return;
        }
        String sessionId = sessionId(request);
        if (sessionId == null) {
            SecurityErrorWriter.writeError(request, response, ApiError.NOT_AUTHENTICATED);
            return;
        }
        IdentityVerifier.VerificationResult result;
        try {
            result = verifier.verify(sessionId, path, RequestLogFilter.requestId(request));
        } catch (RuntimeException unavailable) {
            // Required verification did not complete: refuse before any protected work runs.
            SecurityErrorWriter.writeError(request, response, ApiError.SYSTEM_UNAVAILABLE);
            return;
        }
        if (result.status() == 401) {
            SecurityErrorWriter.writeError(request, response, ApiError.NOT_AUTHENTICATED);
            return;
        }
        if (result.status() == 403) {
            SecurityErrorWriter.writeError(request, response, ApiError.ACCESS_DENIED);
            return;
        }
        if (result.status() != 200) {
            SecurityErrorWriter.writeError(request, response, ApiError.SYSTEM_UNAVAILABLE);
            return;
        }
        if (ProtectedPaths.isAdministrative(path) && !"admin".equals(result.role())) {
            SecurityErrorWriter.writeError(request, response, ApiError.ACCESS_DENIED);
            return;
        }
        chain.doFilter(request, response);
    }

    private static String sessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
