package com.jack.bytejudge.problem.artifact.application;

import com.jack.bytejudge.problem.artifact.domain.model.ArtifactManifest;
import com.jack.bytejudge.problem.artifact.domain.repository.ArtifactManifestRepository;

import java.util.List;

/**
 * Read-side entry used by the problem detail view. It exposes manifest metadata only: digest,
 * size, sanitized file name, kind and the two ordering fields. Object paths never cross this seam.
 */
public final class ArtifactCatalog {
    private final ArtifactManifestRepository manifests;

    public ArtifactCatalog(ArtifactManifestRepository manifests) {
        this.manifests = manifests;
    }

    public List<ArtifactManifest> listByProblemId(long problemId) {
        return List.copyOf(manifests.findLiveByProblemId(problemId));
    }
}
