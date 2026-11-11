package com.jack.bytejudge.problem.artifact.domain.model;

/**
 * Test-data family used by the FR-12 group cap: sample input/output form one group and hidden
 * input/output another. The names are mirrored by the SQL CASE in {@code ProblemArtifactMapper}
 * because the database cannot call this enum; keep the two in sync.
 */
public enum ArtifactFamily {
    SAMPLE,
    HIDDEN
}
