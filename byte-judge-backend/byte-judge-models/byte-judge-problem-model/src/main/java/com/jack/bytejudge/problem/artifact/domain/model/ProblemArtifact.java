package com.jack.bytejudge.problem.artifact.domain.model;

/**
 * A manifest waiting to be inserted. The identifier is assigned by the storage adapter inside the
 * write transaction, matching the draft repository boundary.
 */
public record ProblemArtifact(long problemId, long draftId, String contentDigest, long byteSize,
                              String fileName, ArtifactKind kind, int caseOrder, int displayOrder) {
}
