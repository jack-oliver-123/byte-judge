package com.jack.bytejudge.problem.audit.domain.model;

/** Every management fact the problem service records, regardless of which feature produced it. */
public enum ProblemAuditEvent {
    PROBLEM_CREATE, PROBLEM_UPDATE, PROBLEM_DELETE, ADMIN_ACCESS, ARTIFACT_UPLOAD
}
