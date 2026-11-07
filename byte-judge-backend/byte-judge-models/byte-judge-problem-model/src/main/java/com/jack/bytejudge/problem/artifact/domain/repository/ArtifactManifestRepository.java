package com.jack.bytejudge.problem.artifact.domain.repository;

import com.jack.bytejudge.problem.artifact.domain.model.ArtifactKind;
import com.jack.bytejudge.problem.artifact.domain.model.ArtifactManifest;
import com.jack.bytejudge.problem.artifact.domain.model.ProblemArtifact;

import java.util.List;

/** Manifest storage owned by the problem service; no filesystem concerns leak through here. */
public interface ArtifactManifestRepository {

    boolean liveSlotExists(long draftId, ArtifactKind kind, int caseOrder);

    /**
     * Whether the (sample|hidden, caseOrder) group already has a live manifest. An incomplete
     * group with only its input or only its output still counts as one group.
     */
    boolean liveGroupExists(long draftId, ArtifactKind kind, int caseOrder);

    /** Distinct (sample|hidden, caseOrder) groups among live manifests of the draft. */
    int countLiveGroups(long draftId);

    void insert(ProblemArtifact artifact);

    List<ArtifactManifest> findLiveByProblemId(long problemId);
}
