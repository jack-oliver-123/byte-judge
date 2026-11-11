package com.jack.bytejudge.problem.admin.interfaces.http.vo;

import com.jack.bytejudge.problem.admin.application.result.CreatedProblem;

/** Creation receipt; identifiers are decimal strings so JavaScript clients never lose precision. */
public record CreatedProblemView(String id, String draftId, int problemNo, long revision) {
    public static CreatedProblemView from(CreatedProblem created) {
        return new CreatedProblemView(Long.toString(created.problemId()),
                Long.toString(created.draftId()), created.problemNo(), created.revision());
    }
}
