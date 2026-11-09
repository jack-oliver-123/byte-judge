package com.jack.bytejudge.problem.artifact.application;

import com.jack.bytejudge.problem.artifact.application.command.UploadArtifactCommand;
import com.jack.bytejudge.problem.artifact.application.port.ArtifactStore;
import com.jack.bytejudge.problem.artifact.application.port.ArtifactUnitOfWork;
import com.jack.bytejudge.problem.artifact.application.port.DraftArtifactSeam;
import com.jack.bytejudge.problem.artifact.application.result.ArtifactRegistration;
import com.jack.bytejudge.problem.artifact.domain.ArtifactGroupLimitException;
import com.jack.bytejudge.problem.artifact.domain.ArtifactRules;
import com.jack.bytejudge.problem.artifact.domain.ArtifactSlotOccupiedException;
import com.jack.bytejudge.problem.artifact.domain.ArtifactStorageException;
import com.jack.bytejudge.problem.artifact.domain.ArtifactTooLargeException;
import com.jack.bytejudge.problem.artifact.domain.InvalidArtifactException;
import com.jack.bytejudge.problem.artifact.domain.model.ArtifactKind;
import com.jack.bytejudge.problem.artifact.domain.model.ProblemArtifact;
import com.jack.bytejudge.problem.artifact.domain.repository.ArtifactManifestRepository;
import com.jack.bytejudge.problem.audit.application.port.ProblemAudit;
import com.jack.bytejudge.problem.audit.domain.model.ProblemAuditEntry;

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
    private final ArtifactUnitOfWork unitOfWork;

    public ArtifactRegistrationService(ArtifactStore store, ArtifactManifestRepository manifests,
                                       DraftArtifactSeam drafts, ProblemAudit audit,
                                       ArtifactUnitOfWork unitOfWork) {
        this.store = store;
        this.manifests = manifests;
        this.drafts = drafts;
        this.audit = audit;
        this.unitOfWork = unitOfWork;
    }

    /**
     * Rejections are audited after the failing operation has unwound, so a transaction rollback
     * cannot take the rejection fact with it. Auditing is best-effort: a storage outage must not
     * replace the original rejection with an unrelated failure. Unexpected runtime failures are
     * rethrown without an audit entry, because their category is unknown.
     */
    public ArtifactRegistration register(UploadArtifactCommand command) {
        try {
            return registerOrThrow(command);
        } catch (RuntimeException rejected) {
            String reason = rejectionReason(rejected);
            if (reason != null) {
                reject(command, reason);
            }
            throw rejected;
        }
    }

    private ArtifactRegistration registerOrThrow(UploadArtifactCommand command) {
        ArtifactKind kind = ArtifactKind.parse(command.kind());
        ArtifactRules.validateMetadata(kind, command.caseOrder(), command.displayOrder(),
                command.expectedRevision());
        ArtifactRules.validateDeclaredSize(command.declaredSize());
        String fileName = ArtifactRules.safeFileName(command.fileName());

        ArtifactStore.StoredArtifact stored = store.store(command.content());

        return unitOfWork.execute(() -> registerManifest(command, kind, fileName, stored));
    }

    private ArtifactRegistration registerManifest(UploadArtifactCommand command, ArtifactKind kind,
                                                  String fileName, ArtifactStore.StoredArtifact stored) {
        long draftId = drafts.lockForArtifact(command.problemId(), command.expectedRevision());
        if (manifests.liveSlotExists(draftId, kind, command.caseOrder())) {
            throw new ArtifactSlotOccupiedException();
        }
        // The draft row lock serialises uploads for this draft, so the count cannot be raced by
        // another upload. An incomplete group (only input or only output) already counts.
        if (!manifests.liveGroupExists(draftId, kind, command.caseOrder())
                && manifests.countLiveGroups(draftId) >= ArtifactRules.MAX_GROUPS) {
            throw new ArtifactGroupLimitException(ArtifactRules.MAX_GROUPS);
        }
        long artifactId = manifests.insert(new ProblemArtifact(command.problemId(), draftId,
                stored.contentDigest(), stored.byteSize(), fileName, kind, command.caseOrder(),
                command.displayOrder()));
        if (!drafts.advanceRevision(draftId, command.expectedRevision())) {
            throw new DraftArtifactSeam.RevisionConflictException();
        }
        audit.record(ProblemAuditEntry.artifactUploaded(command.operatorId(),
                command.operatorAccount(), command.problemId(), command.requestId()));
        return new ArtifactRegistration(artifactId, stored.contentDigest(), stored.byteSize(),
                fileName, kind, command.caseOrder(), command.displayOrder());
    }

    private void reject(UploadArtifactCommand command, String reason) {
        try {
            audit.record(ProblemAuditEntry.artifactUploadRejected(command.operatorId(),
                    command.operatorAccount(), reason, command.problemId(), command.requestId()));
        } catch (RuntimeException ignored) {
            // The primary outcome is already determined; leave it untouched.
        }
    }

    /** Maps only the failures this use case can audit; {@code null} means "not a known rejection". */
    private static String rejectionReason(RuntimeException failure) {
        // The order matters: both subclasses must be tested before their InvalidArtifactException
        // parent, otherwise they would be audited under the generic reason.
        if (failure instanceof ArtifactTooLargeException) {
            return "SIZE";
        }
        if (failure instanceof ArtifactGroupLimitException) {
            return "GROUP_LIMIT";
        }
        if (failure instanceof InvalidArtifactException) {
            return "INVALID";
        }
        if (failure instanceof ArtifactStorageException) {
            return "STORAGE";
        }
        if (failure instanceof DraftArtifactSeam.DraftNotFoundException) {
            return "NOT_FOUND";
        }
        if (failure instanceof DraftArtifactSeam.RevisionConflictException) {
            return "REVISION";
        }
        if (failure instanceof ArtifactSlotOccupiedException) {
            return "SLOT";
        }
        return null;
    }
}
