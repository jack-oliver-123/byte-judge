package com.jack.bytejudge.problem.admin.application.command;

import com.jack.bytejudge.problem.admin.application.AdminActor;
import com.jack.bytejudge.problem.admin.domain.model.DraftContent;

/**
 * One administrator save of the complete editable field set. The command is assembled at the
 * protocol boundary and carries no Servlet or HTTP type.
 */
public record SaveDraftCommand(long problemId, long expectedRevision, DraftContent content,
                               AdminActor actor, String requestId) {
}
