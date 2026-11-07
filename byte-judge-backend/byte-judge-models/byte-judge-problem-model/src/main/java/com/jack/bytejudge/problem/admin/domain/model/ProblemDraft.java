package com.jack.bytejudge.problem.admin.domain.model;

import java.time.Instant;

/** One persisted draft revision together with its audit metadata. */
public record ProblemDraft(long id, long problemId, long revision, DraftContent content,
                           long createdById, long updatedById, Instant createdAt, Instant updatedAt,
                           boolean deleted) {
}
