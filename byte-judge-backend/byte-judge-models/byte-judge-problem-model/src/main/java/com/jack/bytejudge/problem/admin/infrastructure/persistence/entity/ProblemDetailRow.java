package com.jack.bytejudge.problem.admin.infrastructure.persistence.entity;

import java.time.LocalDateTime;

/** Joined problem/draft projection used by detail and locked-detail reads. */
public record ProblemDetailRow(long problemId, int problemNo, String status, boolean everPublished,
                               long createdById, boolean problemDeleted, LocalDateTime createdAt,
                               long draftId, long draftProblemId, long revision, String title,
                               String statement, String inputDescription, String outputDescription,
                               String difficulty, String tags, Integer timeLimitCpuMs,
                               Integer timeLimitWallMs, Integer memoryLimitMiB,
                               boolean draftDeleted, long draftCreatedById, long draftUpdatedById,
                               LocalDateTime draftCreatedAt, LocalDateTime updatedAt) {
}
