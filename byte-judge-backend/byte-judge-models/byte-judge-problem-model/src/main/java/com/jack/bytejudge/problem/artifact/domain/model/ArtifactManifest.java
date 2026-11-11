package com.jack.bytejudge.problem.artifact.domain.model;

/**
 * One immutable manifest reference. Files are content-addressed on the protected volume, so the
 * same bytes may be referenced by several manifests; the digest and size are metadata, never a
 * locator exposed to callers.
 */
public record ArtifactManifest(long id, String contentDigest, long byteSize, String fileName,
                               ArtifactKind kind, int caseOrder, int displayOrder) {
}
