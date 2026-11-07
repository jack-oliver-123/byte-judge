package com.jack.bytejudge.problem.artifact.domain;

/**
 * The draft already holds a live artifact for the same kind and case order. Uploads never replace
 * silently: a replacement would change the draft's test data without an explicit revision bump
 * request for the replaced slot. The caller must refresh and decide.
 */
public final class ArtifactSlotOccupiedException extends RuntimeException {
    public ArtifactSlotOccupiedException() {
        super("The artifact slot is already occupied.", null, false, false);
    }
}
