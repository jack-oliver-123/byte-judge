package com.jack.bytejudge.problem.admin.domain.model;

import com.jack.bytejudge.problem.admin.domain.DraftValidationException;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The frozen editable field set of one problem draft (spec 002 D3). Construction always applies
 * the draft-time limits; persistence never relies on column widths to reject content.
 *
 * <p>Title and tags are stored in their stripped form. Text fields are stored verbatim because
 * leading/trailing whitespace may be meaningful in a statement. The 100 KiB cap covers the
 * statement plus both descriptions, measured in UTF-8 bytes.
 */
public record DraftContent(String title, String statement, String inputDescription,
                           String outputDescription, Difficulty difficulty, List<String> tags,
                           Integer timeLimitCpuMs, Integer timeLimitWallMs, Integer memoryLimitMiB) {

    public static final int TITLE_MAX_CODE_POINTS = 100;
    public static final int TEXT_MAX_UTF8_BYTES = 100 * 1024;
    public static final int TAGS_MAX = 5;
    public static final int TAG_MAX_CODE_POINTS = 20;
    public static final int CPU_MIN_MS = 100;
    public static final int CPU_MAX_MS = 10_000;
    public static final int WALL_MIN_MS = 100;
    public static final int WALL_MAX_MS = 30_000;
    public static final int MEMORY_MIN_MIB = 64;
    public static final int MEMORY_MAX_MIB = 2_048;

    public DraftContent {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** Title-only draft created by {@code POST /admin/problems}. */
    public static DraftContent titleOnly(String title) {
        return of(title, null, null, null, null, List.of(), null, null, null);
    }

    /** Validates and normalizes the complete editable field set. */
    public static DraftContent of(String title, String statement, String inputDescription,
                                  String outputDescription, Difficulty difficulty, List<String> tags,
                                  Integer timeLimitCpuMs, Integer timeLimitWallMs,
                                  Integer memoryLimitMiB) {
        String normalizedTitle = validateTitle(title);
        validateTextSize(statement, inputDescription, outputDescription);
        List<String> normalizedTags = validateTags(tags);
        validateLimits(timeLimitCpuMs, timeLimitWallMs, memoryLimitMiB);
        return new DraftContent(normalizedTitle, statement, inputDescription, outputDescription,
                difficulty, normalizedTags, timeLimitCpuMs, timeLimitWallMs, memoryLimitMiB);
    }

    private static String validateTitle(String title) {
        if (title == null) {
            throw new DraftValidationException("Title is required.");
        }
        String normalized = stripUnicodeSpace(title);
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (normalized.isBlank() || codePoints < 1 || codePoints > TITLE_MAX_CODE_POINTS) {
            throw new DraftValidationException("Title must contain 1-100 non-blank code points.");
        }
        return normalized;
    }

    private static void validateTextSize(String statement, String inputDescription, String outputDescription) {
        long bytes = utf8Length(statement) + utf8Length(inputDescription) + utf8Length(outputDescription);
        if (bytes > TEXT_MAX_UTF8_BYTES) {
            throw new DraftValidationException("Statement and descriptions exceed 100 KiB.");
        }
    }

    private static long utf8Length(String value) {
        return value == null ? 0L : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static List<String> validateTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        if (tags.size() > TAGS_MAX) {
            throw new DraftValidationException("At most five tags are allowed.");
        }
        List<String> normalized = new java.util.ArrayList<>(tags.size());
        for (String tag : tags) {
            if (tag == null) {
                throw new DraftValidationException("A tag cannot be null.");
            }
            String stripped = stripUnicodeSpace(tag);
            int codePoints = stripped.codePointCount(0, stripped.length());
            if (stripped.isBlank() || codePoints < 1 || codePoints > TAG_MAX_CODE_POINTS) {
                throw new DraftValidationException("Each tag must contain 1-20 non-blank code points.");
            }
            normalized.add(stripped);
        }
        return List.copyOf(normalized);
    }

    private static String stripUnicodeSpace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (end > start && isSpace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static void validateLimits(Integer cpuMs, Integer wallMs, Integer memoryMiB) {
        if (cpuMs != null && (cpuMs < CPU_MIN_MS || cpuMs > CPU_MAX_MS)) {
            throw new DraftValidationException("CPU time limit is out of range.");
        }
        if (wallMs != null && (wallMs < WALL_MIN_MS || wallMs > WALL_MAX_MS)) {
            throw new DraftValidationException("Wall time limit is out of range.");
        }
        if (cpuMs != null && wallMs != null && wallMs < cpuMs) {
            throw new DraftValidationException("Wall time must not be below CPU time.");
        }
        if (memoryMiB != null && (memoryMiB < MEMORY_MIN_MIB || memoryMiB > MEMORY_MAX_MIB)) {
            throw new DraftValidationException("Memory limit is out of range.");
        }
    }
}
