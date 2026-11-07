package com.jack.bytejudge.problem.admin.application;

/**
 * Escapes user text before it is turned into a SQL LIKE contains pattern. {@code !} is the chosen
 * escape character, so {@code %}, {@code _} and {@code !} are matched literally instead of acting
 * as wildcards. The value is always bound as a parameter.
 */
public final class LikePattern {
    private LikePattern() {
    }

    /** Returns a trimmed contains pattern, or {@code null} when no filter should be applied. */
    public static String contains(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return "%" + escape(raw.strip()) + "%";
    }

    static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '!' || current == '%' || current == '_') {
                escaped.append('!');
            }
            escaped.append(current);
        }
        return escaped.toString();
    }
}
