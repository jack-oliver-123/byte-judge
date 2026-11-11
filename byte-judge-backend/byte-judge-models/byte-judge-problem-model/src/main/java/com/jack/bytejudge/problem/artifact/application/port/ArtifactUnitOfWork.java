package com.jack.bytejudge.problem.artifact.application.port;

import java.util.function.Supplier;

/**
 * The write boundary for manifest registration. Storage happens before this boundary; the manifest
 * row, the draft revision advance and the success audit must all commit or roll back together.
 * Keeping the boundary behind a port makes the ordering testable without a database.
 */
public interface ArtifactUnitOfWork {

    <T> T execute(Supplier<T> work);
}
