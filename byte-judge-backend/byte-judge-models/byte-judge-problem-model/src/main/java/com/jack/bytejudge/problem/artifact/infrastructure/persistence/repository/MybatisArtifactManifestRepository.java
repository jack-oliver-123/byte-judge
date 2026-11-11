package com.jack.bytejudge.problem.artifact.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.jack.bytejudge.problem.artifact.domain.ArtifactSlotOccupiedException;
import com.jack.bytejudge.problem.artifact.domain.model.ArtifactKind;
import com.jack.bytejudge.problem.artifact.domain.model.ArtifactManifest;
import com.jack.bytejudge.problem.artifact.domain.model.ProblemArtifact;
import com.jack.bytejudge.problem.artifact.domain.repository.ArtifactManifestRepository;
import com.jack.bytejudge.problem.artifact.infrastructure.persistence.entity.ArtifactManifestEntity;
import com.jack.bytejudge.problem.artifact.infrastructure.persistence.entity.ProblemArtifactEntity;
import com.jack.bytejudge.problem.artifact.infrastructure.persistence.mapper.ProblemArtifactMapper;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

/**
 * Manifest adapter. Identifiers are generated here, inside the caller's write transaction, so the
 * application layer never depends on the identifier implementation.
 */
public final class MybatisArtifactManifestRepository implements ArtifactManifestRepository {
    private final ProblemArtifactMapper mapper;
    private final IdentifierGenerator identifiers;

    public MybatisArtifactManifestRepository(ProblemArtifactMapper mapper,
                                             IdentifierGenerator identifiers) {
        this.mapper = mapper;
        this.identifiers = identifiers;
    }

    @Override
    public boolean liveSlotExists(long draftId, ArtifactKind kind, int caseOrder) {
        return mapper.countLiveSlot(draftId, kind.name(), caseOrder) > 0;
    }

    @Override
    public boolean liveGroupExists(long draftId, ArtifactKind kind, int caseOrder) {
        return mapper.countLiveGroup(draftId, kind.family().name(), caseOrder) > 0;
    }

    @Override
    public int countLiveGroups(long draftId) {
        return mapper.countLiveGroups(draftId);
    }

    @Override
    public long insert(ProblemArtifact artifact) {
        long id = identifiers.nextId(artifact).longValue();
        try {
            mapper.insert(new ProblemArtifactEntity(id, artifact.problemId(), artifact.draftId(),
                    artifact.contentDigest(), artifact.byteSize(), artifact.fileName(),
                    artifact.kind().name(), artifact.caseOrder(), artifact.displayOrder()));
        } catch (DuplicateKeyException raced) {
            // Two uploads passed the pre-check; the unique slot key is the final arbiter.
            throw new ArtifactSlotOccupiedException();
        }
        return id;
    }

    @Override
    public List<ArtifactManifest> findLiveByProblemId(long problemId) {
        return mapper.findLiveByProblemId(problemId).stream()
                .map(MybatisArtifactManifestRepository::toManifest)
                .toList();
    }

    private static ArtifactManifest toManifest(ArtifactManifestEntity row) {
        return new ArtifactManifest(row.id(), row.contentDigest(), row.byteSize(), row.fileName(),
                ArtifactKind.valueOf(row.kind()), row.caseOrder(), row.displayOrder());
    }
}
