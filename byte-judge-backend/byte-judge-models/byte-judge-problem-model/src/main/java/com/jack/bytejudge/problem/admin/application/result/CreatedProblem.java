package com.jack.bytejudge.problem.admin.application.result;

/** Result of creating a draft: the stable problem identity, allocated number and first revision. */
public record CreatedProblem(long problemId, int problemNo, long draftId, long revision) {
}
