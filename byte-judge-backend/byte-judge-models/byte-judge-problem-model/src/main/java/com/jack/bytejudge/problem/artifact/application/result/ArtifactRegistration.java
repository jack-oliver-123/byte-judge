package com.jack.bytejudge.problem.artifact.application.result;

import com.jack.bytejudge.problem.artifact.domain.model.ArtifactKind;

/** Safe receipt for a committed manifest. It deliberately contains no filesystem locator. */
public record ArtifactRegistration(long artifactId, String contentDigest, long byteSize,
                                   String fileName, ArtifactKind kind, int caseOrder,
                                   int displayOrder) {
}
