package com.jack.bytejudge.problem.artifact.interfaces.http;

import com.jack.bytejudge.common.core.response.ApiError;
import com.jack.bytejudge.common.core.response.ApiResponse;
import com.jack.bytejudge.common.web.CommonErrorAdvice;
import com.jack.bytejudge.common.web.RequestLogFilter;
import com.jack.bytejudge.problem.admin.application.DraftVersionConflictException;
import com.jack.bytejudge.problem.admin.domain.AdminIdentityRequiredException;
import com.jack.bytejudge.problem.admin.domain.DraftValidationException;
import com.jack.bytejudge.problem.admin.domain.IdentityAccessDeniedException;
import com.jack.bytejudge.problem.admin.domain.ProblemNotDeletableException;
import com.jack.bytejudge.problem.admin.domain.ProblemNotFoundException;
import com.jack.bytejudge.problem.admin.interfaces.http.vo.ProblemDetailView;
import com.jack.bytejudge.problem.artifact.application.port.DraftArtifactSeam;
import com.jack.bytejudge.problem.artifact.domain.ArtifactSlotOccupiedException;
import com.jack.bytejudge.problem.artifact.domain.ArtifactStorageException;
import com.jack.bytejudge.problem.artifact.domain.InvalidArtifactException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Problem-service error exit. It extends the shared MVC fallback so framework errors keep the
 * frozen response shape, and adds the artifact and draft-specific classifications. There is
 * exactly one global advice bean in this service; services with their own controllers must extend
 * or amend this class rather than adding a second catch-all.
 */
@RestControllerAdvice
public final class ProblemErrorAdvice extends CommonErrorAdvice {

    @ExceptionHandler(AdminIdentityRequiredException.class)
    ResponseEntity<ApiResponse<Void>> identityRequired(HttpServletRequest request,
                                                       AdminIdentityRequiredException failure) {
        return respond(ApiError.NOT_AUTHENTICATED, request, failure);
    }

    @ExceptionHandler(IdentityAccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> identityDenied(HttpServletRequest request,
                                                     IdentityAccessDeniedException failure) {
        return respond(ApiError.ACCESS_DENIED, request, failure);
    }

    @ExceptionHandler(DraftValidationException.class)
    ResponseEntity<ApiResponse<Void>> invalidDraft(HttpServletRequest request,
                                                   DraftValidationException failure) {
        return respond(ApiError.INVALID_REQUEST, request, failure);
    }

    @ExceptionHandler(ProblemNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> missingProblem(HttpServletRequest request,
                                                     ProblemNotFoundException failure) {
        return respond(ApiError.NOT_FOUND, request, failure);
    }

    @ExceptionHandler(ProblemNotDeletableException.class)
    ResponseEntity<ApiResponse<Void>> notDeletable(HttpServletRequest request,
                                                   ProblemNotDeletableException failure) {
        return respond(ApiError.INVALID_REQUEST, request, failure);
    }

    /**
     * Save conflicts echo the current draft, in the same flat detail shape as a successful GET, so
     * the administrator can rebase the edit (D3). This is the one error body with data, so it
     * cannot use the shared {@code respond} helper.
     */
    @ExceptionHandler(DraftVersionConflictException.class)
    ResponseEntity<ApiResponse<ProblemDetailView>> draftConflict(
            HttpServletRequest request, DraftVersionConflictException failure) {
        return ResponseEntity.status(ApiError.VERSION_CONFLICT.status())
                .body(ApiResponse.failure(ApiError.VERSION_CONFLICT,
                        ProblemDetailView.from(failure.current()),
                        RequestLogFilter.requestId(request)));
    }

    /** A required persistence read/write could not complete; protected work must not look successful. */
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiResponse<Void>> storageUnavailable(HttpServletRequest request,
                                                         DataAccessException failure) {
        return respond(ApiError.SYSTEM_UNAVAILABLE, request, failure);
    }

    @ExceptionHandler(InvalidArtifactException.class)
    ResponseEntity<ApiResponse<Void>> invalidArtifact(HttpServletRequest request,
                                                      InvalidArtifactException failure) {
        return respond(ApiError.INVALID_REQUEST, request, failure);
    }

    @ExceptionHandler(ArtifactSlotOccupiedException.class)
    ResponseEntity<ApiResponse<Void>> occupiedSlot(HttpServletRequest request,
                                                   ArtifactSlotOccupiedException failure) {
        return respond(ApiError.INVALID_REQUEST, request, failure);
    }

    @ExceptionHandler(ArtifactStorageException.class)
    ResponseEntity<ApiResponse<Void>> storageFailure(HttpServletRequest request,
                                                     ArtifactStorageException failure) {
        return respond(ApiError.SYSTEM_UNAVAILABLE, request, failure);
    }

    @ExceptionHandler(DraftArtifactSeam.DraftNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> missingDraft(HttpServletRequest request,
                                                   DraftArtifactSeam.DraftNotFoundException failure) {
        return respond(ApiError.NOT_FOUND, request, failure);
    }

    @ExceptionHandler(DraftArtifactSeam.RevisionConflictException.class)
    ResponseEntity<ApiResponse<Void>> revisionConflict(HttpServletRequest request,
                                                       DraftArtifactSeam.RevisionConflictException failure) {
        return respond(ApiError.VERSION_CONFLICT, request, failure);
    }

    /** Container-level multipart limits and malformed multipart bodies are client errors. */
    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiResponse<Void>> multipart(HttpServletRequest request, MultipartException failure) {
        return respond(ApiError.INVALID_REQUEST, request, failure);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiResponse<Void>> missingPart(HttpServletRequest request,
                                                  MissingServletRequestPartException failure) {
        return respond(ApiError.INVALID_REQUEST, request, failure);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiResponse<Void>> missingParameter(HttpServletRequest request,
                                                       MissingServletRequestParameterException failure) {
        return respond(ApiError.INVALID_REQUEST, request, failure);
    }
}
