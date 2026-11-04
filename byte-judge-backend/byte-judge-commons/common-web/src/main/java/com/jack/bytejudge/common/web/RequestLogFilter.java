package com.jack.bytejudge.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.UUID;

public final class RequestLogFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID = "byteJudge.requestId";
    public static final String HEADER = "X-Request-ID";

    /** The correlation id of the current request, or an empty string outside this filter. */
    public static String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(REQUEST_ID);
        return id == null ? "" : id.toString();
    }

    private final boolean ingress;

    public RequestLogFilter(boolean ingress) {
        this.ingress = ingress;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String id = !ingress && supplied != null && supplied.matches("[A-Za-z0-9_-]{1,64}")
                ? supplied : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID, id);
        response.setHeader(HEADER, id);
        response.setHeader("Cache-Control", "no-store");
        long start = System.nanoTime();
        MDC.put("requestId", id);
        var forwarded = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                return HEADER.equalsIgnoreCase(name) ? id : super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                return HEADER.equalsIgnoreCase(name) ? Collections.enumeration(java.util.List.of(id))
                        : super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                var names = Collections.list(super.getHeaderNames());
                names.removeIf(HEADER::equalsIgnoreCase);
                names.add(HEADER);
                return Collections.enumeration(names);
            }
        };
        try {
            chain.doFilter(forwarded, response);
        } finally {
            // Fixed fields only: never headers, bodies, cookies, query values or parser exception messages.
            LoggerFactory.getLogger("bytejudge.http").info("http requestId={} method={} status={} durationMs={}",
                    id, request.getMethod(), response.getStatus(), (System.nanoTime() - start) / 1_000_000);
            MDC.remove("requestId");
        }
    }
}
