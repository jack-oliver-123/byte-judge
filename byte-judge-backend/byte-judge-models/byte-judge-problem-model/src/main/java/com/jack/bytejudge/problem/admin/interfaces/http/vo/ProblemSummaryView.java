package com.jack.bytejudge.problem.admin.interfaces.http.vo;

import com.jack.bytejudge.problem.admin.domain.model.ProblemListItem;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** One back-office list row. */
public record ProblemSummaryView(String id, int problemNo, String title, String difficulty,
                                 String status, long revision, String createdAt, String updatedAt) {
    public static ProblemSummaryView from(ProblemListItem item) {
        return new ProblemSummaryView(Long.toString(item.problemId()), item.problemNo(), item.title(),
                item.difficulty() == null ? null : item.difficulty().name(), item.status().name(),
                item.revision(), utc(item.createdAt()), utc(item.updatedAt()));
    }

    static String utc(Instant value) {
        return value == null ? null : DateTimeFormatter.ISO_INSTANT.format(value);
    }
}
