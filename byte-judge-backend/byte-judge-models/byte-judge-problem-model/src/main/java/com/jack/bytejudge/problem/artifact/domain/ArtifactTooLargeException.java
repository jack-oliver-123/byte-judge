package com.jack.bytejudge.problem.artifact.domain;

/** The streamed content exceeded the per-file bound. Reported as {@code INVALID_REQUEST}. */
public final class ArtifactTooLargeException extends InvalidArtifactException {
    public ArtifactTooLargeException(long limit) {
        super("Artifact exceeds the " + limit + " byte limit.");
    }
}
