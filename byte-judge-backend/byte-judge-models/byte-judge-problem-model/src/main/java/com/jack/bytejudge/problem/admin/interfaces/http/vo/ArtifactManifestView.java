package com.jack.bytejudge.problem.admin.interfaces.http.vo;

import com.jack.bytejudge.problem.artifact.domain.model.ArtifactManifest;

/** Manifest metadata only; the storage path and file bytes never cross this boundary. */
public record ArtifactManifestView(String artifactId, String contentDigest, long byteSize,
                                   String fileName, String kind, int caseOrder, int displayOrder) {
    public static ArtifactManifestView from(ArtifactManifest manifest) {
        return new ArtifactManifestView(Long.toString(manifest.id()), manifest.contentDigest(),
                manifest.byteSize(), manifest.fileName(), manifest.kind().name(),
                manifest.caseOrder(), manifest.displayOrder());
    }
}
