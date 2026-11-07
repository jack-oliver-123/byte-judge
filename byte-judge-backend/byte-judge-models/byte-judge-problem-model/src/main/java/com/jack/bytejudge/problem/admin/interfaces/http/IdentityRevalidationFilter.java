package com.jack.bytejudge.problem.admin.interfaces.http;

import com.jack.bytejudge.common.core.response.ApiError;
import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.common.web.ProtectedPaths;
import com.jack.bytejudge.common.web.RequestLogFilter;
import com.jack.bytejudge.common.web.SecurityErrorWriter;
import com.jack.bytejudge.problem.admin.application.port.IdentityVerifier;
import com.jack.bytejudge.problem.admin.application.port.ProblemAudit;
import com.jack.bytejudge.problem.admin.domain.IdentityAccessDeniedException;
import com.jack.bytejudge.problem.admin.domain.model.VerifiedIdentity;
import com.jack.bytejudge.problem.admin.domain.model.ProblemAuditEntry;
import com.jack.bytejudge.problem.admin.domain.model.ProblemAuditEvent;
import com.jack.bytejudge.problem.admin.domain.model.ProblemAuditOutcome;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Before protected work runs, the shared-session identity is verified against the user service's
 * authoritative storage. Verification covers session liveness, revocation, account status, role
 * and authentication epoch; the user service unavailable means the request fails closed. The
 * verified answer replaces the session snapshot for this request, so a stale role can never pass.
 */
public final class IdentityRevalidationFilter extends OncePerRequestFilter {
    private final IdentityVerifier verifier;
    private final ProblemAudit audit;

    public IdentityRevalidationFilter(IdentityVerifier verifier,
                                      ProblemAudit audit) {
        this.verifier = verifier;
        this.audit = audit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ProtectedPaths.isProtected(ProtectedPaths.routingPath(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof SessionIdentity identity)) {
            // Anonymous requests are decided by Spring Security's entry point, not here.
            chain.doFilter(request, response);
            return;
        }
        var session = request.getSession(false);
        if (session == null) {
            SecurityContextHolder.clearContext();
            SecurityErrorWriter.writeError(request, response, ApiError.NOT_AUTHENTICATED);
            return;
        }
        String requestId = RequestLogFilter.requestId(request);
        Optional<VerifiedIdentity> verified;
        try {
            verified = verifier.verify(session.getId(), ProtectedPaths.routingPath(request), requestId);
        } catch (IdentityAccessDeniedException denied) {
            audit.record(new ProblemAuditEntry(ProblemAuditEvent.ADMIN_ACCESS, ProblemAuditOutcome.DENIED,
                    identity.userId(), identity.account(), "ROLE", null, requestId));
            SecurityErrorWriter.writeError(request, response, ApiError.ACCESS_DENIED);
            return;
        } catch (RuntimeException unavailable) {
            // Required authority verification could not complete: protected work must not run.
            SecurityErrorWriter.writeError(request, response, ApiError.SYSTEM_UNAVAILABLE);
            return;
        }
        if (verified.isEmpty()) {
            invalidate(session);
            audit.record(new ProblemAuditEntry(ProblemAuditEvent.ADMIN_ACCESS,
                    ProblemAuditOutcome.REJECTED, identity.userId(), identity.account(),
                    "SESSION", null, requestId));
            SecurityErrorWriter.writeError(request, response, ApiError.NOT_AUTHENTICATED);
            return;
        }
        var current = verified.get();
        if (current.userId() != identity.userId()) {
            invalidate(session);
            SecurityErrorWriter.writeError(request, response, ApiError.NOT_AUTHENTICATED);
            return;
        }
        // A fresh context avoids mutating the shared session's Authentication snapshot.
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new SessionIdentity(current.userId(), current.account(), current.role(), identity.authEpoch()),
                null, List.of(new SimpleGrantedAuthority(SessionIdentity.ROLE_PREFIX + current.role()))));
        SecurityContextHolder.setContext(context);
        if (!ProtectedPaths.isAdministrative(ProtectedPaths.routingPath(request))
                || "admin".equals(current.role())) {
            chain.doFilter(request, response);
            return;
        }
        audit.record(new ProblemAuditEntry(ProblemAuditEvent.ADMIN_ACCESS, ProblemAuditOutcome.DENIED,
                current.userId(), current.account(), "ROLE", null, requestId));
        SecurityErrorWriter.writeError(request, response, ApiError.ACCESS_DENIED);
    }

    private static void invalidate(HttpSession session) {
        try {
            session.invalidate();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
