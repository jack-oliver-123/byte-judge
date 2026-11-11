package com.jack.bytejudge.problem.admin.application.command;

import com.jack.bytejudge.problem.admin.application.AdminActor;

/**
 * One logical deletion request for a draft problem, including the revision the administrator saw.
 */
public record DeleteProblemCommand(long problemId, long expectedRevision, AdminActor actor,
                                   String requestId) {
}
