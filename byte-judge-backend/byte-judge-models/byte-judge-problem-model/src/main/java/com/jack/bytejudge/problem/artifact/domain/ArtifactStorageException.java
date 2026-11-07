package com.jack.bytejudge.problem.artifact.domain;

/**
 * The protected volume could not be read or written. The cause stays internal: messages never
 * carry filesystem paths, digests of unverified content or file bytes.
 */
public class ArtifactStorageException extends RuntimeException {
    public ArtifactStorageException(String classification) {
        super(classification, null, false, false);
    }

    public ArtifactStorageException(String classification, Throwable cause) {
        // Suppression and stack traces are disabled so the internal path never reaches a log line
        // through a chained exception; the classification alone is enough to correlate failures.
        super(classification, null, false, false);
    }
}
