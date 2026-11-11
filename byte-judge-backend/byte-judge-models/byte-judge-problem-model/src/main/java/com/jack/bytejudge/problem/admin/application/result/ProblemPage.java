package com.jack.bytejudge.problem.admin.application.result;

import com.jack.bytejudge.problem.admin.domain.model.ProblemListItem;

import java.util.List;

/** One stable page of the back-office problem list. */
public record ProblemPage(List<ProblemListItem> items, long total, int pageNum, int pageSize) {
}
