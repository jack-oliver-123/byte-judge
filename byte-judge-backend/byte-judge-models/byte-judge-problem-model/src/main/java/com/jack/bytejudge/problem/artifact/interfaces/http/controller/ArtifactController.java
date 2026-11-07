package com.jack.bytejudge.problem.artifact.interfaces.http.controller;

import com.jack.bytejudge.common.core.response.ApiResponse;
import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.common.web.RequestLogFilter;
import com.jack.bytejudge.problem.artifact.application.ArtifactRegistrationService;
import com.jack.bytejudge.problem.artifact.application.command.UploadArtifactCommand;
import com.jack.bytejudge.problem.artifact.domain.InvalidArtifactException;
import com.jack.bytejudge.problem.artifact.interfaces.http.dto.UploadArtifactRequest;
import com.jack.bytejudge.problem.artifact.interfaces.http.vo.ArtifactView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Management upload endpoint for test-data files. Authentication, administrator role, CSRF and
 * live identity revalidation are supplied by the service filter chain; this controller only
 * translates the multipart contract into a use-case command.
 */
@RestController
public final class ArtifactController {
    private final ArtifactRegistrationService registrations;

    public ArtifactController(ArtifactRegistrationService registrations) {
        this.registrations = registrations;
    }

    @PostMapping(value = "/api/v1/admin/problems/{problemId}/draft/artifacts",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<ArtifactView> upload(@PathVariable("problemId") long problemId,
                                     @RequestPart("file") MultipartFile file,
                                     @RequestParam("kind") String kind,
                                     @RequestParam("caseOrder") int caseOrder,
                                     @RequestParam("displayOrder") int displayOrder,
                                     @RequestParam("expectedRevision") long expectedRevision,
                                     @AuthenticationPrincipal SessionIdentity identity,
                                     HttpServletRequest request) {
        UploadArtifactRequest upload = new UploadArtifactRequest(file, kind, caseOrder,
                displayOrder, expectedRevision);
        try (InputStream content = upload.file().getInputStream()) {
            var registration = registrations.register(new UploadArtifactCommand(problemId,
                    upload.expectedRevision(), upload.kind(), upload.caseOrder(), upload.displayOrder(),
                    upload.file().getSize(), upload.file().getOriginalFilename(), content,
                    identity.userId(), identity.account(), RequestLogFilter.requestId(request)));
            return ApiResponse.success(ArtifactView.of(registration),
                    RequestLogFilter.requestId(request));
        } catch (IOException unreadable) {
            throw new InvalidArtifactException("The uploaded content could not be read.");
        }
    }
}
