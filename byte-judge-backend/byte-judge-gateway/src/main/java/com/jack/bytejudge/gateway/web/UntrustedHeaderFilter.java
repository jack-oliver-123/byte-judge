package com.jack.bytejudge.gateway.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Drops headers that only the platform may set: client-supplied user identity, internal
 * authentication and forwarding headers. Downstream services therefore never see a forged
 * {@code X-User-*} identity header, which keeps authentication cookie-based (04 §4.2). The filter
 * strips silently; a client sending these headers gets no special error.
 */
public final class UntrustedHeaderFilter extends OncePerRequestFilter {
    private static final List<String> EXACT = List.of(
            "Forwarded", "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto",
            "X-Forwarded-Port", "X-Forwarded-Prefix");

    static boolean isUntrusted(String name) {
        return name != null && (name.regionMatches(true, 0, "X-User-", 0, 7)
                || name.regionMatches(true, 0, "X-Internal-", 0, 11)
                || EXACT.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(name)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        chain.doFilter(new SanitizedRequest(request), response);
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    private static final class SanitizedRequest extends HttpServletRequestWrapper {
        private SanitizedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            return isUntrusted(name) ? null : super.getHeader(name);
        }

        @Override
        public java.util.Enumeration<String> getHeaders(String name) {
            return isUntrusted(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public java.util.Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.removeIf(UntrustedHeaderFilter::isUntrusted);
            return Collections.enumeration(names);
        }

        @Override
        public int getIntHeader(String name) {
            return isUntrusted(name) ? -1 : super.getIntHeader(name);
        }

        @Override
        public long getDateHeader(String name) {
            return isUntrusted(name) ? -1 : super.getDateHeader(name);
        }
    }
}
