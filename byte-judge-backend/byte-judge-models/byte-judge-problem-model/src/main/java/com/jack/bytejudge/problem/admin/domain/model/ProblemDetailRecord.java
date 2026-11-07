package com.jack.bytejudge.problem.admin.domain.model;

import java.time.Instant;

/**
 * Stable problem identity joined with its current draft. {@code deleted} is true when either the
 * problem or the draft was logically deleted; detail and edit use cases treat both as not found.
 */
public record ProblemDetailRecord(long problemId, int problemNo, ProblemStatus status,
                                  boolean everPublished, long createdById, Instant createdAt,
                                  ProblemDraft draft, boolean deleted) {
}
