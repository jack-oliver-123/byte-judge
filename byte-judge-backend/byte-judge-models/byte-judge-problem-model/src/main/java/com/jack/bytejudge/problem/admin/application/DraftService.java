package com.jack.bytejudge.problem.admin.application;

import com.jack.bytejudge.problem.admin.application.result.CreatedProblem;
import com.jack.bytejudge.problem.admin.application.result.DeletedProblem;
import com.jack.bytejudge.problem.admin.application.result.ProblemDetail;
import com.jack.bytejudge.problem.admin.application.result.ProblemPage;
import com.jack.bytejudge.problem.admin.application.port.ProblemAudit;
import com.jack.bytejudge.problem.admin.domain.DraftValidationException;
import com.jack.bytejudge.problem.admin.domain.ProblemNotDeletableException;
import com.jack.bytejudge.problem.admin.domain.ProblemNotFoundException;
import com.jack.bytejudge.problem.admin.domain.model.Difficulty;
import com.jack.bytejudge.problem.admin.domain.model.DraftContent;
import com.jack.bytejudge.problem.admin.domain.model.ProblemDetailRecord;
import com.jack.bytejudge.problem.admin.domain.model.ProblemDraft;
import com.jack.bytejudge.problem.admin.domain.model.ProblemStatus;
import com.jack.bytejudge.problem.admin.domain.model.ProblemAuditEntry;
import com.jack.bytejudge.problem.admin.domain.model.ProblemAuditEvent;
import com.jack.bytejudge.problem.admin.domain.model.ProblemAuditOutcome;
import com.jack.bytejudge.problem.admin.domain.repository.ProblemDraftRepository;
import com.jack.bytejudge.problem.artifact.application.ArtifactCatalog;
import com.jack.bytejudge.problem.artifact.application.port.DraftArtifactSeam;
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
        audit.record(new ProblemAuditEntry(ProblemAuditEvent.PROBLEM_CREATE,
                ProblemAuditOutcome.SUCCESS, actor.userId(), actor.account(), null, problemId, requestId));
        return new CreatedProblem(problemId, problemNo, draftId, 0);
    }

    @Transactional(readOnly = true)
    public ProblemPage list(String titleContains, String difficultyText, boolean includeDeleted,
                            int pageNum, int pageSize) {
        if (pageNum < 1 || !ALLOWED_PAGE_SIZES.contains(pageSize)) {
            throw new DraftValidationException("Invalid pagination parameters.");
        }
        Difficulty difficulty = Difficulty.parse(difficultyText);
        String title = LikePattern.contains(titleContains);
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
        ProblemDetail detail = toDetail(
                requireLive(drafts.findDetail(problemId).orElseThrow(ProblemNotFoundException::new)));
        audit.record(new ProblemAuditEntry(ProblemAuditEvent.ADMIN_ACCESS,
                ProblemAuditOutcome.SUCCESS, actor.userId(), actor.account(), "PREVIEW", problemId,
                requestId));
        return detail;
    }

    /**
     * Replaces the complete editable field set. The revision advances for every accepted save,
     * including a save whose content is unchanged.
     */
    @Transactional
    public ProblemDetail save(long problemId, long expectedRevision, DraftContent content,
                              AdminActor actor, String requestId) {
        ProblemDetailRecord current = requireLiveLocked(problemId);
        if (current.draft().revision() != expectedRevision) {
            throw new DraftVersionConflictException(toDetail(current));
        }
        boolean updated = drafts.updateDraft(problemId, current.draft().id(), expectedRevision,
                content, actor.userId());
        if (!updated) {
            throw new DraftVersionConflictException(
                    toDetail(requireLive(drafts.findDetail(problemId).orElseThrow(ProblemNotFoundException::new))));
        }
        audit.record(new ProblemAuditEntry(ProblemAuditEvent.PROBLEM_UPDATE,
                ProblemAuditOutcome.SUCCESS, actor.userId(), actor.account(), null, problemId, requestId));
        return toDetail(requireLive(drafts.findDetail(problemId).orElseThrow(ProblemNotFoundException::new)));
    }

    /**
     * Logical deletion of a draft that never had publication history. Repeating the same DELETE
     * with the revision that was already accepted is an idempotent success; every other state of a
     * deleted problem is reported as not found.
     */
    @Transactional
    public DeletedProblem delete(long problemId, long expectedRevision, AdminActor actor,
                                 String requestId) {
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
        boolean deleted = drafts.markDeleted(problemId, current.draft().id(), actor.userId());
        if (!deleted) {
            ProblemDetailRecord reread = drafts.findDetail(problemId)
                    .orElseThrow(ProblemNotFoundException::new);
            if (reread.deleted() && reread.draft().revision() == expectedRevision) {
                return new DeletedProblem(problemId, reread.problemNo(), reread.draft().revision());
            }
            throw new DraftVersionConflictException(toDetail(reread));
        }
        audit.record(new ProblemAuditEntry(ProblemAuditEvent.PROBLEM_DELETE,
                ProblemAuditOutcome.SUCCESS, actor.userId(), actor.account(), null, problemId, requestId));
        return new DeletedProblem(problemId, current.problemNo(), current.draft().revision());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LockedDraft lockForArtifact(long problemId, long expectedRevision) {
        ProblemDraftRepository.LockedDraft locked = drafts.lockLiveDraft(problemId);
        if (locked == null) {
            throw new DraftNotFoundException();
        }
        if (locked.revision() != expectedRevision) {
            throw new RevisionConflictException();
        }
        return new LockedDraft(locked.draftId(), locked.problemId(), locked.revision(),
                locked.createdById());
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
