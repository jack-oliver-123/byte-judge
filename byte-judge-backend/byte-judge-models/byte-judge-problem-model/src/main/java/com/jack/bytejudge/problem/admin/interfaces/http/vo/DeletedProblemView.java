package com.jack.bytejudge.problem.admin.interfaces.http.vo;

import com.jack.bytejudge.problem.admin.application.result.DeletedProblem;

/** Receipt for an accepted or idempotently repeated logical delete. */
public record DeletedProblemView(String id, int problemNo, String status, long revision) {
    public static DeletedProblemView from(DeletedProblem deleted) {
        return new DeletedProblemView(Long.toString(deleted.problemId()), deleted.problemNo(),
                "DELETED", deleted.revision());
    }
}
