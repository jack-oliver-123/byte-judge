package com.jack.bytejudge.problem.admin.interfaces.http.dto;

import com.jack.bytejudge.problem.admin.domain.model.Difficulty;
import com.jack.bytejudge.problem.admin.domain.model.DraftContent;

import java.util.List;

/**
 * PATCH body carrying the complete editable field set. Every key must be present; explicit
 * {@code null} clears nullable fields. {@code expectedRevision} is mandatory and checked before saving.
 */
public record SaveDraftRequest(String title, String statement, String inputDescription,
                               String outputDescription, String difficulty, List<String> tags,
                               Integer timeLimitCpuMs, Integer timeLimitWallMs,
                               Integer memoryLimitMiB, Long expectedRevision) {

    public DraftContent toContent() {
        return DraftContent.of(title, statement, inputDescription, outputDescription,
                Difficulty.parse(difficulty), tags, timeLimitCpuMs, timeLimitWallMs, memoryLimitMiB);
    }
}
