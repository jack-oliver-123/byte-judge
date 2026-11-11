package com.jack.bytejudge.problem.artifact.infrastructure.persistence.entity;

/** Read-side manifest projection; the adapter converts {@code kind} back to the domain enum. */
public record ArtifactManifestEntity(long id, String contentDigest, long byteSize, String fileName,
                                     String kind, int caseOrder, int displayOrder) {
}
