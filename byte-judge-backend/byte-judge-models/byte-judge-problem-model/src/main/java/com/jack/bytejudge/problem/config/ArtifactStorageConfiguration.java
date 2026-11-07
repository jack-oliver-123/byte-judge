package com.jack.bytejudge.problem.config;

import com.jack.bytejudge.problem.artifact.application.port.ArtifactStore;
import com.jack.bytejudge.problem.artifact.domain.ArtifactRules;
import com.jack.bytejudge.problem.artifact.infrastructure.storage.FileSystemArtifactStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Storage wiring for test-data artifacts, kept separate from the security configuration. The root
 * comes from deployment configuration and must point at a volume outside the web root; it is never
 * echoed in a response. The 1 MiB bound is a content rule, not a deployment preference.
 */
@Configuration(proxyBeanMethods = false)
public class ArtifactStorageConfiguration {

    @Bean
    ArtifactStore artifactStore(@Value("${byte-judge.storage.artifact-root}") String artifactRoot) {
        if (artifactRoot == null || artifactRoot.isBlank()) {
            throw new IllegalStateException("byte-judge.storage.artifact-root must be configured.");
        }
        return new FileSystemArtifactStore(Path.of(artifactRoot), ArtifactRules.MAX_BYTES);
    }
}
