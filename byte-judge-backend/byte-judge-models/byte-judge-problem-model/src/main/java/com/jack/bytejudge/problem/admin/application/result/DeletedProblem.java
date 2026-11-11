package com.jack.bytejudge.problem.admin.application.result;

/** Result of an accepted (or idempotently repeated) logical delete. */
public record DeletedProblem(long problemId, int problemNo, long revision) {
}
