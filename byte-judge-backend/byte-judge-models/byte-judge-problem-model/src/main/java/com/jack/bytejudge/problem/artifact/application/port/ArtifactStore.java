package com.jack.bytejudge.problem.artifact.application.port;

import com.jack.bytejudge.problem.artifact.domain.ArtifactStorageException;
import com.jack.bytejudge.problem.artifact.domain.InvalidArtifactException;

import java.io.InputStream;

/**
 * Stores artifact bytes on the protected volume. Implementations stream with a hard bound, accept
 * zero-byte content (FR-12 permits an empty input or expected output), address objects by their
 * SHA-256 digest and publish with an atomic move.
 */
public interface ArtifactStore {

    /**
     * Stores the stream and returns its verified identity. The caller owns the input stream.
     *
     * @throws InvalidArtifactException when the content exceeds the configured bound
     * @throws ArtifactStorageException when the volume fails or a pre-existing object does not
     *                                  match its digest/size
     */
    StoredArtifact store(InputStream content);

    record StoredArtifact(String contentDigest, long byteSize) {
    }
}
