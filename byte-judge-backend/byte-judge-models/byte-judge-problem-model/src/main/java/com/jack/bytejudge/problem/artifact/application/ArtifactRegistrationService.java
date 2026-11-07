package com.jack.bytejudge.problem.artifact.application;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.jack.bytejudge.problem.admin.application.port.ProblemAudit;
import com.jack.bytejudge.problem.admin.domain.model.ProblemAuditEntry;
import com.jack.bytejudge.problem.admin.domain.model.ProblemAuditEvent;
import com.jack.bytejudge.problem.admin.domain.model.ProblemAuditOutcome;
import com.jack.bytejudge.problem.artifact.application.command.UploadArtifactCommand;
import com.jack.bytejudge.problem.artifact.application.port.ArtifactStore;
import com.jack.bytejudge.problem.artifact.application.port.ArtifactUnitOfWork;
import com.jack.bytejudge.problem.artifact.application.port.DraftArtifactSeam;
import com.jack.bytejudge.problem.artifact.application.result.ArtifactRegistration;
import com.jack.bytejudge.problem.artifact.domain.ArtifactRules;
import com.jack.bytejudge.problem.artifact.domain.ArtifactGroupLimitException;
import com.jack.bytejudge.problem.artifact.domain.ArtifactSlotOccupiedException;
import com.jack.bytejudge.problem.artifact.domain.ArtifactStorageException;
import com.jack.bytejudge.problem.artifact.domain.ArtifactTooLargeException;
import com.jack.bytejudge.problem.artifact.domain.InvalidArtifactException;
import com.jack.bytejudge.problem.artifact.domain.model.ArtifactKind;
import com.jack.bytejudge.problem.artifact.domain.model.ProblemArtifact;
import com.jack.bytejudge.problem.artifact.domain.repository.ArtifactManifestRepository;

/**
 * Registers one test-data manifest.
 *
 * <p>Ordering is part of the contract: bytes are written, hashed and atomically published
 * <em>before</em> the database transaction starts. Inside one transaction the draft row is locked,
 * the manifest is inserted, the revision is advanced and the success audit is written. A failure
 * rolls the manifest and revision back together; the already-published object stays unreferenced
 * until a later controlled cleanup; this slice does not run a garbage collector.
 */
public final class ArtifactRegistrationService {
    private final ArtifactStore store;
    private final ArtifactManifestRepository manifests;
    private final DraftArtifactSeam drafts;
    private final ProblemAudit audit;
    private final IdentifierGenerator identifiers;
    private final ArtifactUnitOfWork unitOfWork;

    public ArtifactRegistrationService(ArtifactStore store, ArtifactManifestRepository manifests,
                                       DraftArtifactSeam drafts, ProblemAudit audit,
                                       IdentifierGenerator identifiers, ArtifactUnitOfWork unitOfWork) {
        this.store = store;
        this.manifests = manifests;
        this.drafts = drafts;
        this.audit = audit;
        this.identifiers = identifiers;
        this.unitOfWork = unitOfWork;
    }

    public ArtifactRegistration register(UploadArtifactCommand command) {
        ArtifactKind kind;
        String fileName;
        try {
            kind = ArtifactKind.parse(command.kind());
            ArtifactRules.validateMetadata(kind, command.caseOrder(), command.displayOrder(),
                    command.expectedRevision());
            ArtifactRules.validateDeclaredSize(command.declaredSize());
            fileName = ArtifactRules.safeFileName(command.fileName());
        } catch (InvalidArtifactException rejected) {
            reject(command, reasonOf(rejected));
            throw rejected;
        }

        ArtifactStore.StoredArtifact stored;
        try {
            stored = store.store(command.content());
        } catch (InvalidArtifactException rejected) {
            reject(command, reasonOf(rejected));
            throw rejected;
        } catch (ArtifactStorageException failed) {
            reject(command, "STORAGE");
            throw failed;
        }

        try {
            return unitOfWork.execute(() -> registerManifest(command, kind, fileName, stored));
        } catch (DraftArtifactSeam.DraftNotFoundException missing) {
            reject(command, "NOT_FOUND");
            throw missing;
        } catch (DraftArtifactSeam.RevisionConflictException conflict) {
            reject(command, "REVISION");
            throw conflict;
        } catch (ArtifactSlotOccupiedException occupied) {
            reject(command, "SLOT");
            throw occupied;
        } catch (ArtifactGroupLimitException limit) {
            reject(command, "GROUP_LIMIT");
            throw limit;
        }
    }

    private ArtifactRegistration registerManifest(UploadArtifactCommand command, ArtifactKind kind,
                                                  String fileName, ArtifactStore.StoredArtifact stored) {
        DraftArtifactSeam.LockedDraft draft = drafts.lockForArtifact(command.problemId(),
                command.expectedRevision());
        if (manifests.liveSlotExists(draft.draftId(), kind, command.caseOrder())) {
            throw new ArtifactSlotOccupiedException();
        }
        // The draft row lock serialises uploads for this draft, so the count cannot be raced by
        // another upload. An incomplete group (only input or only output) already counts.
        if (!manifests.liveGroupExists(draft.draftId(), kind, command.caseOrder())
                && manifests.countLiveGroups(draft.draftId()) >= ArtifactRules.MAX_GROUPS) {
            throw new ArtifactGroupLimitException(ArtifactRules.MAX_GROUPS);
        }
        long artifactId = identifiers.nextId(command).longValue();
        ProblemArtifact artifact = new ProblemArtifact(artifactId, command.problemId(), draft.draftId(),
                stored.contentDigest(), stored.byteSize(), fileName, kind, command.caseOrder(),
                command.displayOrder());
        manifests.insert(artifact);
        if (!drafts.advanceRevision(draft.draftId(), command.expectedRevision())) {
            throw new DraftArtifactSeam.RevisionConflictException();
        }
        audit.record(new ProblemAuditEntry(ProblemAuditEvent.ARTIFACT_UPLOAD,
                ProblemAuditOutcome.SUCCESS, command.operatorId(), command.operatorAccount(),
                "REGISTERED", command.problemId(), command.requestId()));
        return new ArtifactRegistration(artifactId, stored.contentDigest(), stored.byteSize(),
                fileName, kind, command.caseOrder(), command.displayOrder());
    }

    /**
     * Rejections are audited after the write transaction has rolled back, otherwise the audit row
     * would disappear with it. Auditing is best-effort here: a storage outage must not replace the
     * original rejection with an unrelated failure.
     */
    private void reject(UploadArtifactCommand command, String reason) {
        try {
            audit.record(new ProblemAuditEntry(ProblemAuditEvent.ARTIFACT_UPLOAD,
                    ProblemAuditOutcome.REJECTED, command.operatorId(), command.operatorAccount(),
                    reason, command.problemId(), command.requestId()));
        } catch (RuntimeException ignored) {
            // The primary outcome is already determined; leave it untouched.
        }
    }

    private static String reasonOf(InvalidArtifactException rejected) {
        return rejected instanceof ArtifactTooLargeException ? "SIZE" : "INVALID";
    }
}
