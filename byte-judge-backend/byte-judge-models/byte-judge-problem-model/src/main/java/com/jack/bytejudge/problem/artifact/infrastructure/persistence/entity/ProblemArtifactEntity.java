package com.jack.bytejudge.problem.artifact.infrastructure.persistence.entity;

/** Flat row passed to the artifact insert statement; {@code kind} is the stored enum name. */
public record ProblemArtifactEntity(long id, long problemId, long draftId, String contentDigest,
                                    long byteSize, String fileName, String kind, int caseOrder,
                                    int displayOrder) {
}
