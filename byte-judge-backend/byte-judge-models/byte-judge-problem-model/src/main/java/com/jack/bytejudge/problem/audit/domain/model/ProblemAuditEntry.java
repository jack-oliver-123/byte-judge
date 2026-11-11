package com.jack.bytejudge.problem.audit.domain.model;

/**
 * Metadata only: request bodies, credentials and test data never belong in the audit trail.
 *
 * <p>The named factories are the supported construction path. They keep the event/outcome pair
 * out of positional argument lists, so a caller cannot silently record the wrong action by
 * swapping two neighbouring arguments.
 */
public record ProblemAuditEntry(ProblemAuditEvent event, ProblemAuditOutcome outcome,
                                Long operatorId, String operatorAccount, String reason,
                                Long problemId, String requestId) {

    public static ProblemAuditEntry problemCreated(long operatorId, String operatorAccount,
                                                   long problemId, String requestId) {
        return success(ProblemAuditEvent.PROBLEM_CREATE, operatorId, operatorAccount, null,
                problemId, requestId);
    }

    public static ProblemAuditEntry problemUpdated(long operatorId, String operatorAccount,
                                                   long problemId, String requestId) {
        return success(ProblemAuditEvent.PROBLEM_UPDATE, operatorId, operatorAccount, null,
                problemId, requestId);
    }

    public static ProblemAuditEntry problemDeleted(long operatorId, String operatorAccount,
                                                   long problemId, String requestId) {
        return success(ProblemAuditEvent.PROBLEM_DELETE, operatorId, operatorAccount, null,
                problemId, requestId);
    }

    public static ProblemAuditEntry adminPreview(long operatorId, String operatorAccount,
                                                 long problemId, String requestId) {
        return success(ProblemAuditEvent.ADMIN_ACCESS, operatorId, operatorAccount, "PREVIEW",
                problemId, requestId);
    }

    public static ProblemAuditEntry adminAccessDenied(long operatorId, String operatorAccount,
                                                      String reason, String requestId) {
        return new ProblemAuditEntry(ProblemAuditEvent.ADMIN_ACCESS, ProblemAuditOutcome.DENIED,
                operatorId, operatorAccount, reason, null, requestId);
    }

    public static ProblemAuditEntry adminAccessRejected(long operatorId, String operatorAccount,
                                                        String reason, String requestId) {
        return new ProblemAuditEntry(ProblemAuditEvent.ADMIN_ACCESS, ProblemAuditOutcome.REJECTED,
                operatorId, operatorAccount, reason, null, requestId);
    }

    public static ProblemAuditEntry artifactUploaded(long operatorId, String operatorAccount,
                                                     long problemId, String requestId) {
        return success(ProblemAuditEvent.ARTIFACT_UPLOAD, operatorId, operatorAccount, "REGISTERED",
                problemId, requestId);
    }

    public static ProblemAuditEntry artifactUploadRejected(long operatorId, String operatorAccount,
                                                           String reason, long problemId,
                                                           String requestId) {
        return new ProblemAuditEntry(ProblemAuditEvent.ARTIFACT_UPLOAD,
                ProblemAuditOutcome.REJECTED, operatorId, operatorAccount, reason, problemId,
                requestId);
    }

    private static ProblemAuditEntry success(ProblemAuditEvent event, long operatorId,
                                             String operatorAccount, String reason,
                                             long problemId, String requestId) {
        return new ProblemAuditEntry(event, ProblemAuditOutcome.SUCCESS, operatorId,
                operatorAccount, reason, problemId, requestId);
    }
}
