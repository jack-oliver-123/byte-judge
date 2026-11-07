package com.jack.bytejudge.problem.artifact.domain;

/**
 * FR-12 caps the distinct test-case groups (sample and hidden families together) at 20. The
 * counterpart file may still be missing while a draft is incomplete; only a twenty-first distinct
 * group is rejected.
 */
public final class ArtifactGroupLimitException extends InvalidArtifactException {
    public ArtifactGroupLimitException(int limit) {
        super("The draft already holds the maximum of " + limit + " test-case groups.");
    }
}
