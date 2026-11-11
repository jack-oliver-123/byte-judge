package com.jack.bytejudge.problem.artifact.domain;

import com.jack.bytejudge.problem.artifact.domain.model.ArtifactKind;

/**
 * Frozen artifact content rules. These limits are enforced while streaming, not after the file has
 * already been buffered, so an oversized or empty upload never reaches the protected volume.
 */
public final class ArtifactRules {
    /** 1 MiB, matching specification 002 D6. */
    public static final long MAX_BYTES = 1_048_576L;
    public static final int MIN_CASE_ORDER = 1;
    /** FR-12: sample and hidden data together may hold at most 20 groups. */
    public static final int MAX_CASE_ORDER = 20;
    /** The same FR-12 cap counted across both families. */
    public static final int MAX_GROUPS = 20;
    public static final int MIN_DISPLAY_ORDER = 0;
    public static final int MAX_DISPLAY_ORDER = 10_000;
    private static final int MAX_FILE_NAME_CODE_POINTS = 255;
    private static final String FALLBACK_FILE_NAME = "artifact";

    private ArtifactRules() {
    }

    public static void validateMetadata(ArtifactKind kind, int caseOrder, int displayOrder,
                                        long expectedRevision) {
        if (kind == null) {
            throw new InvalidArtifactException("Artifact kind is required.");
        }
        if (caseOrder < MIN_CASE_ORDER || caseOrder > MAX_CASE_ORDER) {
            throw new InvalidArtifactException("caseOrder is outside the accepted range.");
        }
        if (displayOrder < MIN_DISPLAY_ORDER || displayOrder > MAX_DISPLAY_ORDER) {
            throw new InvalidArtifactException("displayOrder is outside the accepted range.");
        }
        if (expectedRevision < 0) {
            throw new InvalidArtifactException("expectedRevision must not be negative.");
        }
    }

    /**
     * FR-12 allows an empty input or expected output, so zero bytes is valid as long as the
     * multipart field itself is present. Only a negative (unknown) size and the 1 MiB bound are
     * rejected here; the streaming bound remains authoritative.
     */
    public static void validateDeclaredSize(long declaredSize) {
        if (declaredSize < 0) {
            throw new InvalidArtifactException("The declared artifact size is invalid.");
        }
        if (declaredSize > MAX_BYTES) {
            throw new ArtifactTooLargeException(MAX_BYTES);
        }
    }

    /**
     * Keeps only display metadata. The name is never used to build a filesystem path, so the
     * sanitisation additionally removes path separators and control characters to keep every
     * consumer of the value safe.
     */
    public static String safeFileName(String original) {
        if (original == null) {
            return FALLBACK_FILE_NAME;
        }
        String name = original;
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (separator >= 0) {
            name = name.substring(separator + 1);
        }
        StringBuilder cleaned = new StringBuilder(name.length());
        name.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .forEach(cleaned::appendCodePoint);
        name = cleaned.toString().strip();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            return FALLBACK_FILE_NAME;
        }
        if (name.codePointCount(0, name.length()) > MAX_FILE_NAME_CODE_POINTS) {
            name = name.substring(0, name.offsetByCodePoints(0, MAX_FILE_NAME_CODE_POINTS));
        }
        return name;
    }
}
