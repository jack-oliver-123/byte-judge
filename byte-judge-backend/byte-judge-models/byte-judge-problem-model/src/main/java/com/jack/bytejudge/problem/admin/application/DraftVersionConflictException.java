package com.jack.bytejudge.problem.admin.application;

import com.jack.bytejudge.problem.admin.application.result.ProblemDetail;

/**
 * A save or delete carried a stale revision. The current detail is echoed so the administrator can
 * rebase their edit; the HTTP adapter renders it as the 409 body's {@code data}.
 */
public final class DraftVersionConflictException extends RuntimeException {
    private final transient ProblemDetail current;

    public DraftVersionConflictException(ProblemDetail current) {
        super("The draft revision changed concurrently.", null, false, false);
        this.current = current;
    }

    public ProblemDetail current() {
        return current;
    }
}
