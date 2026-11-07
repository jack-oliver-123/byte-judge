package com.jack.bytejudge.problem.admin.domain.model;

import java.time.Instant;

/** Back-office list row. It intentionally carries no statement or artifact data. */
public record ProblemListItem(long problemId, int problemNo, String title, Difficulty difficulty,
                              ProblemStatus status, long revision, Instant createdAt, Instant updatedAt) {
}
