package com.jack.bytejudge.problem.artifact.domain.model;

/** A manifest row waiting to be inserted; the identifier is assigned inside the write transaction. */
public record ProblemArtifact(long id, long problemId, long draftId, String contentDigest,
                              long byteSize, String fileName, ArtifactKind kind, int caseOrder,
                              int displayOrder) {
}
