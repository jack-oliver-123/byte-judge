package com.jack.bytejudge.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.UUID;
import java.util.regex.Pattern;

public final class RequestLogFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID = "byteJudge.requestId";
    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";
    /** Accept only a bounded opaque token; never trust arbitrary external text as a log field. */
    private static final Pattern ACCEPTED_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Logger HTTP_LOG = LoggerFactory.getLogger("bytejudge.http");

    /** The correlation id of the current request, or an empty string outside this filter. */
    public static String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(REQUEST_ID);
        return id == null ? "" : id.toString();
    }

    /** The correlation id of the current thread's in-flight request, or an empty string. */
    public static String currentRequestId() {
        String id = MDC.get(MDC_KEY);
        return id == null ? "" : id;
    }

    private final boolean ingress;

    public RequestLogFilter(boolean ingress) {
        this.ingress = ingress;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String id = !ingress && supplied != null && ACCEPTED_ID.matcher(supplied).matches()
                ? supplied : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID, id);
        response.setHeader(HEADER, id);
        response.setHeader("Cache-Control", "no-store");
        long start = System.nanoTime();
        MDC.put(MDC_KEY, id);
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
            // Fixed fields only: the URI has no query string, and headers, bodies, cookies, query
            // values and parser exception messages are never logged. The path is safe here because
            // no API route carries a secret in its path; keep it that way when adding routes.
            int status = response.getStatus();
            HTTP_LOG.atLevel(status >= 500 ? Level.WARN : Level.INFO)
                    .log("http requestId={} method={} path={} status={} durationMs={}",
                            id, request.getMethod(), request.getRequestURI(), status,
                            (System.nanoTime() - start) / 1_000_000);
            MDC.remove(MDC_KEY);
        }
    }
}
