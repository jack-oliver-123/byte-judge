package com.jack.bytejudge.problem.artifact.infrastructure.persistence.repository;

import com.jack.bytejudge.problem.artifact.domain.ArtifactSlotOccupiedException;
import com.jack.bytejudge.problem.artifact.domain.model.ArtifactKind;
import com.jack.bytejudge.problem.artifact.domain.model.ArtifactManifest;
import com.jack.bytejudge.problem.artifact.domain.model.ProblemArtifact;
import com.jack.bytejudge.problem.artifact.domain.repository.ArtifactManifestRepository;
import com.jack.bytejudge.problem.artifact.infrastructure.persistence.mapper.ProblemArtifactMapper;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

public final class MybatisArtifactManifestRepository implements ArtifactManifestRepository {
    private final ProblemArtifactMapper mapper;

    public MybatisArtifactManifestRepository(ProblemArtifactMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean liveSlotExists(long draftId, ArtifactKind kind, int caseOrder) {
        return mapper.countLiveSlot(draftId, kind, caseOrder) > 0;
    }

    @Override
    public boolean liveGroupExists(long draftId, ArtifactKind kind, int caseOrder) {
        return mapper.countLiveGroup(draftId, family(kind), caseOrder) > 0;
    }

    @Override
    public int countLiveGroups(long draftId) {
        return mapper.countLiveGroups(draftId);
    }

    private static String family(ArtifactKind kind) {
        return kind.hidden() ? "HIDDEN" : "SAMPLE";
    }

    @Override
    public void insert(ProblemArtifact artifact) {
        try {
            mapper.insert(artifact);
        } catch (DuplicateKeyException raced) {
            // Two uploads passed the pre-check; the unique slot key is the final arbiter.
            throw new ArtifactSlotOccupiedException();
        }
    }

    @Override
    public List<ArtifactManifest> findLiveByProblemId(long problemId) {
        return mapper.findLiveByProblemId(problemId);
    }
}
