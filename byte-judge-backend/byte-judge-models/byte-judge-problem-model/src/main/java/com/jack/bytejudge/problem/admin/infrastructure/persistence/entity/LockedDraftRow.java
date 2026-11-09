package com.jack.bytejudge.problem.admin.infrastructure.persistence.entity;

/** Locked live-draft projection used by artifact registration's draft seam. */
public record LockedDraftRow(long draftId, long problemId, long revision, long createdById) {
}
