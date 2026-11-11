package com.jack.bytejudge.problem.admin.application.result;

import com.jack.bytejudge.problem.admin.domain.model.DraftContent;
import com.jack.bytejudge.problem.admin.domain.model.ProblemStatus;
import com.jack.bytejudge.problem.artifact.domain.model.ArtifactManifest;

import java.time.Instant;
import java.util.List;

/** Full administrator view of one live draft, including manifest metadata (never file content). */
public record ProblemDetail(long problemId, int problemNo, ProblemStatus status, boolean everPublished,
                            long revision, long draftId, DraftContent content, Instant createdAt,
                            Instant updatedAt, List<ArtifactManifest> artifacts) {
    public ProblemDetail {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }
}
