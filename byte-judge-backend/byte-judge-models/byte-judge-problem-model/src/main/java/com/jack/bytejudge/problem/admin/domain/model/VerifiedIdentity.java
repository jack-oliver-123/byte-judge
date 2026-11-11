package com.jack.bytejudge.problem.admin.domain.model;

/**
 * The three safe identity fields confirmed by the user service for a live session. No session
 * artifact, credential or contact field travels here.
 */
public record VerifiedIdentity(long userId, String account, String role) {
}
