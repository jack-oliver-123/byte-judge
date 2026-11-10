package com.jack.bytejudge.problem.admin.application;

import com.jack.bytejudge.problem.admin.application.command.DeleteProblemCommand;
import com.jack.bytejudge.problem.admin.application.command.SaveDraftCommand;
import com.jack.bytejudge.problem.admin.application.result.CreatedProblem;
import com.jack.bytejudge.problem.admin.application.result.DeletedProblem;
import com.jack.bytejudge.problem.admin.application.result.ProblemDetail;
import com.jack.bytejudge.problem.admin.application.result.ProblemPage;
import com.jack.bytejudge.problem.admin.domain.DraftValidationException;
import com.jack.bytejudge.problem.admin.domain.ProblemNotDeletableException;
import com.jack.bytejudge.problem.admin.domain.ProblemNotFoundException;
import com.jack.bytejudge.problem.admin.domain.model.Difficulty;
import com.jack.bytejudge.problem.admin.domain.model.DraftContent;
import com.jack.bytejudge.problem.admin.domain.model.ProblemDetailRecord;
import com.jack.bytejudge.problem.admin.domain.model.ProblemDraft;
import com.jack.bytejudge.problem.admin.domain.model.ProblemStatus;
import com.jack.bytejudge.problem.admin.domain.repository.ProblemDraftRepository;
import com.jack.bytejudge.problem.artifact.application.ArtifactCatalog;
import com.jack.bytejudge.problem.artifact.application.port.DraftArtifactSeam;
import com.jack.bytejudge.problem.audit.application.port.ProblemAudit;
import com.jack.bytejudge.problem.audit.domain.model.ProblemAuditEntry;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Draft lifecycle use cases. Every write and its success audit share one transaction, so a
 * persisted problem/draft/revision never exists without its audit fact and vice versa.
 *
 * <p>The two {@link DraftArtifactSeam} methods deliberately require an existing transaction: the
 * artifact feature registers a manifest and advances the revision atomically.
 */
public class DraftService implements DraftArtifactSeam {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 20, 50);

    private final ProblemDraftRepository drafts;
    private final ProblemAudit audit;
    private final ArtifactCatalog artifacts;

    public DraftService(ProblemDraftRepository drafts, ProblemAudit audit, ArtifactCatalog artifacts) {
        this.drafts = drafts;
        this.audit = audit;
        this.artifacts = artifacts;
    }

    /** Title-only creation allocates the permanent problem number and the first revision (0). */
    @Transactional
    public CreatedProblem create(String title, AdminActor actor, String requestId) {
        DraftContent content = DraftContent.titleOnly(title);
        int problemNo = drafts.allocateProblemNo();
        long problemId = drafts.insertProblem(problemNo, actor.userId());
        long draftId = drafts.insertDraft(problemId, content, actor.userId());
        drafts.attachDraft(problemId, draftId);
        audit.record(ProblemAuditEntry.problemCreated(actor.userId(), actor.account(), problemId,
                requestId));
        return new CreatedProblem(problemId, problemNo, draftId, 0);
    }

    @Transactional(readOnly = true)
    public ProblemPage list(String titleContains, String difficultyText, boolean includeDeleted,
                            int pageNum, int pageSize) {
        if (pageNum < 1 || !ALLOWED_PAGE_SIZES.contains(pageSize)) {
            throw new DraftValidationException("Invalid pagination parameters.");
        }
        Difficulty difficulty = Difficulty.parse(difficultyText);
        String title = LikePattern.containsPattern(titleContains);
        long offset = ((long) pageNum - 1) * pageSize;
        long total = drafts.count(title, difficulty, includeDeleted);
        List<com.jack.bytejudge.problem.admin.domain.model.ProblemListItem> items = total == 0
                ? List.of()
                : drafts.findPage(title, difficulty, includeDeleted, offset, pageSize);
        return new ProblemPage(items, total, pageNum, pageSize);
    }

    /**
     * Administrator preview. The read is audited as ADMIN_ACCESS/SUCCESS with metadata only, as
     * required for management previews; the not-found path is not confirmed in the audit trail.
     */
    @Transactional
    public ProblemDetail detail(long problemId, AdminActor actor, String requestId) {
        ProblemDetail detail = toDetail(liveDetail(problemId));
        audit.record(ProblemAuditEntry.adminPreview(actor.userId(), actor.account(), problemId,
                requestId));
        return detail;
    }

    /**
     * Replaces the complete editable field set. The revision advances for every accepted save,
     * including a save whose content is unchanged.
     */
    @Transactional
    public ProblemDetail save(SaveDraftCommand command) {
        ProblemDetailRecord current = requireLiveLocked(command.problemId());
        if (current.draft().revision() != command.expectedRevision()) {
            throw new DraftVersionConflictException(toDetail(current));
        }
        boolean updated = drafts.updateDraft(command.problemId(), current.draft().id(),
                command.expectedRevision(), command.content(), command.actor().userId());
        if (!updated) {
            throw new DraftVersionConflictException(toDetail(liveDetail(command.problemId())));
        }
        audit.record(ProblemAuditEntry.problemUpdated(command.actor().userId(),
                command.actor().account(), command.problemId(), command.requestId()));
        return toDetail(liveDetail(command.problemId()));
    }

    /**
     * Logical deletion of a draft that never had publication history. Repeating the same DELETE
     * with the revision that was already accepted is an idempotent success; every other state of a
     * deleted problem is reported as not found.
     */
    @Transactional
    public DeletedProblem delete(DeleteProblemCommand command) {
        long problemId = command.problemId();
        long expectedRevision = command.expectedRevision();
        ProblemDetailRecord current = drafts.lockDetail(problemId)
                .orElseThrow(ProblemNotFoundException::new);
        if (current.deleted()) {
            if (current.draft().revision() == expectedRevision) {
                return new DeletedProblem(problemId, current.problemNo(), current.draft().revision());
            }
            throw new ProblemNotFoundException();
        }
        if (current.draft().revision() != expectedRevision) {
            throw new DraftVersionConflictException(toDetail(current));
        }
        if (current.status() != ProblemStatus.DRAFT || current.everPublished()) {
            throw new ProblemNotDeletableException();
        }
        boolean deleted = drafts.markDeleted(problemId, current.draft().id(),
                command.actor().userId());
        if (!deleted) {
            ProblemDetailRecord reread = drafts.findDetail(problemId)
                    .orElseThrow(ProblemNotFoundException::new);
            if (reread.deleted() && reread.draft().revision() == expectedRevision) {
                return new DeletedProblem(problemId, reread.problemNo(), reread.draft().revision());
            }
            throw new DraftVersionConflictException(toDetail(reread));
        }
        audit.record(ProblemAuditEntry.problemDeleted(command.actor().userId(),
                command.actor().account(), problemId, command.requestId()));
        return new DeletedProblem(problemId, current.problemNo(), current.draft().revision());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long lockForArtifact(long problemId, long expectedRevision) {
        ProblemDraftRepository.LockedDraft locked = drafts.lockLiveDraft(problemId);
        if (locked == null) {
            throw new DraftNotFoundException();
        }
        if (locked.revision() != expectedRevision) {
            throw new RevisionConflictException();
        }
        return locked.draftId();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean advanceRevision(long draftId, long expectedRevision) {
        return drafts.advanceRevision(draftId, expectedRevision);
    }

    private ProblemDetailRecord requireLiveLocked(long problemId) {
        ProblemDetailRecord current = drafts.lockDetail(problemId)
                .orElseThrow(ProblemNotFoundException::new);
        if (current.deleted()) {
            throw new ProblemNotFoundException();
        }
        return current;
    }

    private ProblemDetailRecord liveDetail(long problemId) {
        return requireLive(drafts.findDetail(problemId).orElseThrow(ProblemNotFoundException::new));
    }

    private ProblemDetailRecord requireLive(ProblemDetailRecord current) {
        if (current == null || current.deleted()) {
            throw new ProblemNotFoundException();
        }
        return current;
    }

    private ProblemDetail toDetail(ProblemDetailRecord current) {
        ProblemDraft draft = current.draft();
        return new ProblemDetail(current.problemId(), current.problemNo(), current.status(),
                current.everPublished(), draft.revision(), draft.id(), draft.content(),
                current.createdAt(), draft.updatedAt(), artifacts.listByProblemId(current.problemId()));
    }
}
