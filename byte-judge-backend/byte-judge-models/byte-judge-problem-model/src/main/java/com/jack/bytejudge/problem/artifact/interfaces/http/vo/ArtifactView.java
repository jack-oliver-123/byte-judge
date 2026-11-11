package com.jack.bytejudge.problem.artifact.interfaces.http.vo;

import com.jack.bytejudge.problem.artifact.application.result.ArtifactRegistration;

/**
 * Upload receipt. Identifiers are serialised as decimal strings for JavaScript clients; there is
 * no filesystem path or content in this view.
 */
public record ArtifactView(String artifactId, String contentDigest, long byteSize, String fileName,
                           String kind, int caseOrder, int displayOrder) {

    public static ArtifactView of(ArtifactRegistration registration) {
        return new ArtifactView(Long.toString(registration.artifactId()), registration.contentDigest(),
                registration.byteSize(), registration.fileName(), registration.kind().name(),
                registration.caseOrder(), registration.displayOrder());
    }
}
