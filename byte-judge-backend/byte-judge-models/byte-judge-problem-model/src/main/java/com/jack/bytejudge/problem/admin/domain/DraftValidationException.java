package com.jack.bytejudge.problem.admin.domain;

/**
 * A draft field or list query violates the frozen problem content rules. The message is
 * deliberately internal: API callers receive only the fixed INVALID_REQUEST error contract.
 */
public final class DraftValidationException extends RuntimeException {
    public DraftValidationException() {
        super("The problem draft content is invalid.", null, false, false);
    }

    public DraftValidationException(String internalReason) {
        super(internalReason, null, false, false);
    }
}
