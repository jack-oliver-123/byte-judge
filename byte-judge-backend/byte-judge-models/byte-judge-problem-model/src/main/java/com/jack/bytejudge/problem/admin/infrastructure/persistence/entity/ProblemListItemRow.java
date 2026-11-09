package com.jack.bytejudge.problem.admin.infrastructure.persistence.entity;

import java.time.LocalDateTime;

/** Management-list projection: one row per problem joined with its current draft. */
public record ProblemListItemRow(long problemId, int problemNo, String title, String difficulty,
                                 String status, long revision, LocalDateTime createdAt,
                                 LocalDateTime updatedAt) {
}
