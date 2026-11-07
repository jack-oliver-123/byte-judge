package com.jack.bytejudge.problem.admin.interfaces.http.controller;

import com.jack.bytejudge.common.core.response.ApiResponse;
import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.common.web.RequestLogFilter;
import com.jack.bytejudge.problem.admin.application.AdminActor;
import com.jack.bytejudge.problem.admin.application.DraftService;
import com.jack.bytejudge.problem.admin.application.result.CreatedProblem;
import com.jack.bytejudge.problem.admin.application.result.DeletedProblem;
import com.jack.bytejudge.problem.admin.domain.AdminIdentityRequiredException;
import com.jack.bytejudge.problem.admin.domain.DraftValidationException;
import com.jack.bytejudge.problem.admin.interfaces.http.StrictDraftJson;
import org.springframework.http.MediaType;
import com.jack.bytejudge.problem.admin.interfaces.http.dto.CreateProblemRequest;
import com.jack.bytejudge.problem.admin.interfaces.http.dto.SaveDraftRequest;
import com.jack.bytejudge.problem.admin.interfaces.http.vo.CreatedProblemView;
import com.jack.bytejudge.problem.admin.interfaces.http.vo.DeletedProblemView;
import com.jack.bytejudge.problem.admin.interfaces.http.vo.ProblemDetailView;
import com.jack.bytejudge.problem.admin.interfaces.http.vo.ProblemPageView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator draft workbench. Authentication, role and live account revalidation are enforced
 * by the service's security chain before this controller runs; it never reads identity headers.
 */
@RestController
@RequestMapping("/api/v1/admin/problems")
public class AdminProblemController {

    private final DraftService drafts;

    public AdminProblemController(DraftService drafts) {
        this.drafts = drafts;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<CreatedProblemView> create(@AuthenticationPrincipal SessionIdentity identity,
                                           @RequestBody byte[] rawBody,
                                           HttpServletRequest request) {
        CreateProblemRequest body = StrictDraftJson.read(rawBody, CreateProblemRequest.class);
        String requestId = RequestLogFilter.requestId(request);
        CreatedProblem created = drafts.create(body.title(),
                actor(identity), requestId);
        return ApiResponse.success(CreatedProblemView.from(created), requestId);
    }

    @GetMapping
    ApiResponse<ProblemPageView> list(@RequestParam(required = false) String title,
                                      @RequestParam(required = false) String difficulty,
                                      @RequestParam(defaultValue = "false") boolean includeDeleted,
                                      @RequestParam(defaultValue = "1") int pageNum,
                                      @RequestParam(defaultValue = "20") int pageSize,
                                      HttpServletRequest request) {
        return ApiResponse.success(ProblemPageView.from(
                        drafts.list(title, difficulty, includeDeleted, pageNum, pageSize)),
                RequestLogFilter.requestId(request));
    }

    @GetMapping("/{id}")
    ApiResponse<ProblemDetailView> detail(@AuthenticationPrincipal SessionIdentity identity,
                                          @PathVariable String id, HttpServletRequest request) {
        return ApiResponse.success(ProblemDetailView.from(drafts.detail(parseId(id),
                        actor(identity), RequestLogFilter.requestId(request))),
                RequestLogFilter.requestId(request));
    }

    @PatchMapping(value = "/{id}/draft", consumes = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<ProblemDetailView> save(@AuthenticationPrincipal SessionIdentity identity,
                                        @PathVariable String id,
                                        @RequestBody byte[] rawBody,
                                        HttpServletRequest request) {
        SaveDraftRequest body = StrictDraftJson.read(rawBody, SaveDraftRequest.class);
        if (body.expectedRevision() == null || body.expectedRevision() < 0) {
            throw new DraftValidationException("expectedRevision is required.");
        }
        return ApiResponse.success(ProblemDetailView.from(drafts.save(parseId(id),
                        body.expectedRevision(), body.toContent(), actor(identity),
                        RequestLogFilter.requestId(request))),
                RequestLogFilter.requestId(request));
    }

    @DeleteMapping("/{id}")
    ApiResponse<DeletedProblemView> delete(@AuthenticationPrincipal SessionIdentity identity,
                                           @PathVariable String id,
                                           @RequestParam long expectedRevision,
                                           HttpServletRequest request) {
        DeletedProblem deleted = drafts.delete(parseId(id), expectedRevision, actor(identity),
                RequestLogFilter.requestId(request));
        return ApiResponse.success(DeletedProblemView.from(deleted),
                RequestLogFilter.requestId(request));
    }

    private static AdminActor actor(SessionIdentity identity) {
        if (identity == null) {
            throw new AdminIdentityRequiredException();
        }
        return new AdminActor(identity.userId(), identity.account());
    }

    private static long parseId(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException invalid) {
            throw new DraftValidationException("Invalid problem id.");
        }
    }
}
