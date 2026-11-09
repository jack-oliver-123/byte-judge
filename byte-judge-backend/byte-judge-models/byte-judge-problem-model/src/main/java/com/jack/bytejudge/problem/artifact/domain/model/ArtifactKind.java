package com.jack.bytejudge.problem.artifact.domain.model;

import com.jack.bytejudge.problem.artifact.domain.InvalidArtifactException;

/**
 * The four test-data slots of a problem draft. Names are the wire and storage contract; clients
 * must send one of them verbatim. The Chinese labels shown in the management UI are presentation
 * concerns and are deliberately not stored.
 */
public enum ArtifactKind {
    SAMPLE_INPUT(ArtifactFamily.SAMPLE),
    SAMPLE_OUTPUT(ArtifactFamily.SAMPLE),
    HIDDEN_INPUT(ArtifactFamily.HIDDEN),
    HIDDEN_OUTPUT(ArtifactFamily.HIDDEN);

    private final ArtifactFamily family;

    ArtifactKind(ArtifactFamily family) {
        this.family = family;
    }

    public static ArtifactKind parse(String raw) {
        if (raw != null) {
            for (ArtifactKind kind : values()) {
                if (kind.name().equals(raw)) {
                    return kind;
                }
            }
        }
        throw new InvalidArtifactException("Unknown artifact kind.");
    }

    /** Sample and hidden inputs/outputs count as one group pair for the FR-12 group cap. */
    public ArtifactFamily family() {
        return family;
    }
}
