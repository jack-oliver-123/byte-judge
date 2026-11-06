package com.jack.bytejudge.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * Classifies protected browser paths the same way Spring MVC routes them. The raw request URI can
 * contain percent-encoded aliases that still reach a controller, so routing decisions must use the
 * decoded path; otherwise a stale session could skip live identity revalidation.
 */
public final class ProtectedPaths {
    private ProtectedPaths() { }

    public static boolean isProtected(String path) {
        return path.equals("/api/v1/users/me") || path.startsWith("/api/v1/admin/")
                || path.startsWith("/api/v1/me/");
    }

    public static boolean isAdministrative(String path) {
        return path.startsWith("/api/v1/admin/");
    }

    public static boolean isInternal(String path) {
        return path.startsWith("/internal/");
    }

    /**
     * Decoded routing path, without path parameters or a trailing slash. A malformed escape keeps
     * its raw form, which is then treated as an ordinary path rather than silently unprotected.
     */
    public static String routingPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        int parameters = path.indexOf(';');
        if (parameters >= 0) {
            path = path.substring(0, parameters);
        }
        try {
            path = UriUtils.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformedEscape) {
            // Leave the raw value; the routing layer will reject or miss it on its own terms.
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
