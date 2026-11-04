package com.jack.bytejudge.user.auth.interfaces.internal;

import com.jack.bytejudge.common.core.response.ApiResponse;
import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.common.web.RequestLogFilter;
import com.jack.bytejudge.user.auth.application.IdentityRevalidator;
import com.jack.bytejudge.user.auth.application.port.AuthAudit;
import com.jack.bytejudge.user.account.domain.AccountRole;
import com.jack.bytejudge.user.auth.domain.NotAuthenticatedException;
import com.jack.bytejudge.user.auth.domain.PermissionDeniedException;
import com.jack.bytejudge.user.auth.domain.SessionRejectedException;
import com.jack.bytejudge.user.auth.domain.model.AccountSnapshot;
import com.jack.bytejudge.user.auth.domain.model.AuthAuditEntry;
import com.jack.bytejudge.user.auth.domain.model.AuthEvent;
import com.jack.bytejudge.user.auth.domain.model.AuthOutcome;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gateway entry verification. It reads the shared session by identifier, then rechecks the
 * identity against authoritative storage. The browser's own cookie is never trusted here, and the
 * response exposes only the three safe identity fields.
 */
@RestController
public class IdentityVerificationController {
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final IdentityRevalidator revalidator;
    private final AuthAudit audit;
    private final InternalCallGuard guard;

    public IdentityVerificationController(FindByIndexNameSessionRepository<? extends Session> sessions,
                                          IdentityRevalidator revalidator, AuthAudit audit,
                                          InternalCallGuard guard) {
        this.sessions = sessions;
        this.revalidator = revalidator;
        this.audit = audit;
        this.guard = guard;
    }

    @PostMapping(value = "/internal/v1/identity/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<IdentityVerificationView> verify(@RequestBody byte[] rawBody, HttpServletRequest request) {
        String requestId = RequestLogFilter.requestId(request);
        if (!guard.accepts(request)) {
            audit.record(new AuthAuditEntry(AuthEvent.INTERNAL_VERIFY, AuthOutcome.DENIED, null, null,
                    "SERVICE_CREDENTIAL", requestId));
            throw new InternalAccessDeniedException();
        }
        IdentityVerificationRequest body = IdentityVerificationRequest.parse(rawBody);
        String sessionId = body.sessionId();
        Session session = sessions.findById(sessionId);
        SessionIdentity identity = identityOf(session);
        if (identity == null) {
            delete(sessionId);
            throw new NotAuthenticatedException();
        }
        try {
            AccountSnapshot current = revalidator.revalidate(identity);
            if (body.path().startsWith("/api/v1/admin/")
                    && current.role() != AccountRole.ADMIN) {
                audit.record(new AuthAuditEntry(AuthEvent.ADMIN_ACCESS, AuthOutcome.DENIED,
                        current.userId(), current.account(), "ROLE", requestId));
                throw new PermissionDeniedException();
            }
            return ApiResponse.success(IdentityVerificationView.of(current), requestId);
        } catch (SessionRejectedException rejected) {
            // The stale session is removed on the authoritative side, not merely ignored.
            delete(sessionId);
            audit.record(new AuthAuditEntry(AuthEvent.IDENTITY_CHECK, AuthOutcome.REJECTED,
                    identity.userId(), identity.account(), "AUTHORITATIVE_STATE_CHANGED", requestId));
            throw rejected;
        }
    }

    private void delete(String sessionId) {
        sessions.deleteById(sessionId);
    }

    private static SessionIdentity identityOf(Session session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (!(value instanceof SecurityContext context)) {
            return null;
        }
        Authentication authentication = context.getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof SessionIdentity identity
                ? identity : null;
    }
}
