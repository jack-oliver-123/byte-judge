package com.jack.bytejudge.problem.admin.domain.model;

import com.jack.bytejudge.problem.admin.domain.DraftValidationException;

import java.util.Locale;

/** The three frozen problem difficulties. A draft may leave difficulty unset ({@code null}). */
public enum Difficulty {
    EASY,
    MEDIUM,
    HARD;

    /**
     * Parses an API value or a stored column. Blank means "not set" so an empty query parameter or
     * an explicit empty draft value is treated as absent rather than as a malformed enum.
     */
    public static Difficulty parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new DraftValidationException("Unknown difficulty.");
        }
    }
}
