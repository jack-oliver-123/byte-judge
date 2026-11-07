package com.jack.bytejudge.problem.admin.domain;

/**
 * The problem does not exist or its draft was logically deleted. Deleted and absent are
 * intentionally indistinguishable so the API never confirms a deleted problem number.
 */
public final class ProblemNotFoundException extends RuntimeException {
    public ProblemNotFoundException() {
        super("The problem was not found.", null, false, false);
    }
}
