package com.jack.bytejudge.problem.admin.domain.repository;

import com.jack.bytejudge.problem.admin.domain.model.Difficulty;
import com.jack.bytejudge.problem.admin.domain.model.DraftContent;
import com.jack.bytejudge.problem.admin.domain.model.ProblemDetailRecord;
import com.jack.bytejudge.problem.admin.domain.model.ProblemListItem;

import java.util.List;
import java.util.Optional;

/**
 * Domain storage port for problem identities and their current drafts. The application layer
 * never sees MyBatis types; locking and CAS semantics are part of this contract.
 */
public interface ProblemDraftRepository {

    /**
     * Locks the singleton problem-number row and returns the number to allocate. The caller's
     * transaction must also advance the sequence; both happen in {@link #allocateProblemNo()}.
     */
    int allocateProblemNo();

    long insertProblem(int problemNo, long createdById);

    long insertDraft(long problemId, DraftContent content, long createdById);

    void attachDraft(long problemId, long draftId);

    Optional<ProblemDetailRecord> findDetail(long problemId);

    /** Same projection as {@link #findDetail(long)} but locks the problem and draft rows. */
    Optional<ProblemDetailRecord> lockDetail(long problemId);

    /**
     * CAS update: increments revision even when the content is unchanged.
     *
     * @return true when the row still had {@code expectedRevision} and was updated
     */
    boolean updateDraft(long problemId, long draftId, long expectedRevision, DraftContent content,
                        long updatedById);

    /** CAS revision advance used after an artifact manifest insert in the same transaction. */
    boolean advanceRevision(long draftId, long expectedRevision);

    /** Logically deletes the problem and its draft in one step. */
    boolean markDeleted(long problemId, long draftId, long updatedById);

    List<ProblemListItem> findPage(String escapedTitle, Difficulty difficulty, boolean includeDeleted,
                                   long offset, int limit);

    long count(String escapedTitle, Difficulty difficulty, boolean includeDeleted);

    /**
     * Locks the live DRAFT problem and its draft for artifact registration. Returns null when the
     * problem is missing or logically deleted.
     */
    LockedDraft lockLiveDraft(long problemId);

    record LockedDraft(long draftId, long problemId, long revision, long createdById) {
    }
}
