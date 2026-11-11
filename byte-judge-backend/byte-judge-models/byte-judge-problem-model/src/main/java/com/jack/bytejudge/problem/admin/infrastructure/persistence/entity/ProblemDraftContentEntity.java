package com.jack.bytejudge.problem.admin.infrastructure.persistence.entity;

/**
 * Flat persistence projection of the editable draft columns. Difficulty and tags are already
 * converted to their stored forms by the repository adapter; no domain type crosses this boundary.
 */
public record ProblemDraftContentEntity(String title, String statement, String inputDescription,
                                        String outputDescription, String difficulty,
                                        String tags, Integer timeLimitCpuMs,
                                        Integer timeLimitWallMs, Integer memoryLimitMiB) {
}
