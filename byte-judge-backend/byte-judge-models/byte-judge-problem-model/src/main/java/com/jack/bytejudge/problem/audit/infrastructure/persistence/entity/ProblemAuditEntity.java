package com.jack.bytejudge.problem.audit.infrastructure.persistence.entity;

/** Flat row passed to {@code ProblemAuditMapper}; event and outcome are stored as their names. */
public record ProblemAuditEntity(long id, Long operatorId, String operatorAccount, String event,
                                 String outcome, String reason, Long problemId, String requestId) {
}
