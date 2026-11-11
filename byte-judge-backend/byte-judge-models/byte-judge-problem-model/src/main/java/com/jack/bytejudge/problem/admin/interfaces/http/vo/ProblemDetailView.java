package com.jack.bytejudge.problem.admin.interfaces.http.vo;

import com.jack.bytejudge.problem.admin.application.result.ProblemDetail;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Full administrator draft view. Draft fields are intentionally flat; {@code artifacts} carries
 * manifest metadata only. This same shape is the 409 conflict echo.
 */
public record ProblemDetailView(String id, int problemNo, String status, boolean everPublished,
                                long revision, String draftId, String title, String statement,
                                String inputDescription, String outputDescription, String difficulty,
                                List<String> tags, Integer timeLimitCpuMs, Integer timeLimitWallMs,
                                Integer memoryLimitMiB, String createdAt, String updatedAt,
                                List<ArtifactManifestView> artifacts) {

    public static ProblemDetailView from(ProblemDetail detail) {
        return new ProblemDetailView(Long.toString(detail.problemId()), detail.problemNo(),
                detail.status().name(), detail.everPublished(), detail.revision(),
                Long.toString(detail.draftId()), detail.content().title(), detail.content().statement(),
                detail.content().inputDescription(), detail.content().outputDescription(),
                detail.content().difficulty() == null ? null : detail.content().difficulty().name(),
                detail.content().tags(), detail.content().timeLimitCpuMs(),
                detail.content().timeLimitWallMs(), detail.content().memoryLimitMiB(),
                utc(detail.createdAt()), utc(detail.updatedAt()),
                detail.artifacts().stream().map(ArtifactManifestView::from).toList());
    }

    private static String utc(Instant value) {
        return value == null ? null : DateTimeFormatter.ISO_INSTANT.format(value);
    }
}
