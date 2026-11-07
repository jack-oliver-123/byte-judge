package com.jack.bytejudge.problem.admin.domain;

/**
 * Only a draft that never reached publication may be deleted. This slice always has
 * {@code everPublished=false}, but the rule is enforced so the future publish slice cannot
 * accidentally delete a published problem through the same endpoint.
 */
public final class ProblemNotDeletableException extends RuntimeException {
    public ProblemNotDeletableException() {
        super("Only an unpublished draft may be deleted.", null, false, false);
    }
}
