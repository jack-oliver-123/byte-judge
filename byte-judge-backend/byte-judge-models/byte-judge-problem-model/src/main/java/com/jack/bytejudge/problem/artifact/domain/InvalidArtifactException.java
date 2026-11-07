package com.jack.bytejudge.problem.artifact.domain;

/**
 * The upload cannot be accepted at the protocol or content boundary. The message is internal
 * classification only; API responses use the frozen {@code INVALID_REQUEST} contract.
 */
public class InvalidArtifactException extends RuntimeException {
    public InvalidArtifactException(String reason) {
        super(reason, null, false, false);
    }
}
