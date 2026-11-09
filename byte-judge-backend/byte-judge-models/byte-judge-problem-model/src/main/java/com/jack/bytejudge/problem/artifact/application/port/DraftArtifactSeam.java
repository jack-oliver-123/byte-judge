package com.jack.bytejudge.problem.artifact.application.port;

/**
 * Draft CAS seam supplied by the draft feature. Both methods must run inside the caller's existing
 * database transaction; {@link #lockForArtifact} locks the current draft row with
 * {@code SELECT ... FOR UPDATE}, so a concurrent save or upload is serialised behind the lock.
 */
public interface DraftArtifactSeam {

    /**
     * Locks the live draft of the problem, verifies the revision the caller last read and returns
     * the locked draft identifier.
     *
     * @throws DraftNotFoundException  when the problem or its draft is missing or deleted
     * @throws RevisionConflictException when {@code expectedRevision} no longer matches
     */
    long lockForArtifact(long problemId, long expectedRevision);

    /**
     * Increments the revision from {@code expectedRevision}. Returns {@code false} when the row no
     * longer matches, which the caller treats as a version conflict.
     */
    boolean advanceRevision(long draftId, long expectedRevision);

    final class DraftNotFoundException extends RuntimeException {
        public DraftNotFoundException() {
            super("The problem draft was not found.", null, false, false);
        }
    }

    final class RevisionConflictException extends RuntimeException {
        public RevisionConflictException() {
            super("The draft revision changed concurrently.", null, false, false);
        }
    }
}
