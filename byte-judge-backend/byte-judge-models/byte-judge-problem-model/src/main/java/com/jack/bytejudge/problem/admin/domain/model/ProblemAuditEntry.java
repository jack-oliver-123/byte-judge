package com.jack.bytejudge.problem.admin.domain.model;

/** Metadata only: request bodies, credentials and test data never belong in the audit trail. */
public record ProblemAuditEntry(ProblemAuditEvent event, ProblemAuditOutcome outcome,
                                Long operatorId, String operatorAccount, String reason,
                                Long problemId, String requestId) {}
