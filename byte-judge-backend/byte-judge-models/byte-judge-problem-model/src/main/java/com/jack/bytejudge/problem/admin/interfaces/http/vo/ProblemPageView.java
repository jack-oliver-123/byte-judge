package com.jack.bytejudge.problem.admin.interfaces.http.vo;

import com.jack.bytejudge.problem.admin.application.result.ProblemPage;

import java.util.List;

/** Stable page envelope: totals are computed with the same filters as the rows. */
public record ProblemPageView(List<ProblemSummaryView> items, long total, int pageNum, int pageSize) {
    public static ProblemPageView from(ProblemPage page) {
        return new ProblemPageView(page.items().stream().map(ProblemSummaryView::from).toList(),
                page.total(), page.pageNum(), page.pageSize());
    }
}
