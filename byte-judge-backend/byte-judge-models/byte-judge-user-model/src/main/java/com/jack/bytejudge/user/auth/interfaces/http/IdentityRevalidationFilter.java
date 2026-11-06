package com.jack.bytejudge.user.auth.interfaces.http;

import com.jack.bytejudge.common.core.response.ApiError;
import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.common.web.ProtectedPaths;
import com.jack.bytejudge.common.web.RequestLogFilter;
import com.jack.bytejudge.common.web.SecurityErrorWriter;
import com.jack.bytejudge.user.auth.application.IdentityRevalidator;
import com.jack.bytejudge.user.auth.application.port.AuthAudit;
import com.jack.bytejudge.user.auth.domain.SessionRejectedException;
import com.jack.bytejudge.user.auth.domain.model.AuthAuditEntry;
import com.jack.bytejudge.user.auth.domain.model.AuthEvent;
import com.jack.bytejudge.user.auth.domain.model.AuthOutcome;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Before protected work runs, the session snapshot is checked against the user service's current
 * account status, role and authEpoch. A changed fact invalidates this session immediately, and a
 * storage failure refuses the request instead of allowing it through.
 */
public final class IdentityRevalidationFilter extends OncePerRequestFilter {
    private final IdentityRevalidator revalidator;
    private final AuthAudit audit;

    public IdentityRevalidationFilter(IdentityRevalidator revalidator, AuthAudit audit) {
        this.revalidator = revalidator;
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
            // An authenticated context without its owning session cannot be verified.
            SecurityContextHolder.clearContext();
            SecurityErrorWriter.writeError(request, response, ApiError.NOT_AUTHENTICATED);
            return;
        }
        try {
            revalidator.revalidate(session.getId(), identity);
        } catch (SessionRejectedException rejected) {
            invalidate(session);
            audit.record(new AuthAuditEntry(AuthEvent.IDENTITY_CHECK, AuthOutcome.REJECTED,
                    identity.userId(), identity.account(), rejected.reason(),
                    RequestLogFilter.requestId(request)));
            SecurityErrorWriter.writeError(request, response, ApiError.NOT_AUTHENTICATED);
            return;
        } catch (DataAccessException unavailable) {
            // Required authority lookup could not complete: protected work must not run.
            SecurityErrorWriter.writeError(request, response, ApiError.SYSTEM_UNAVAILABLE);
            return;
        }
        chain.doFilter(request, response);
    }

    private static void invalidate(jakarta.servlet.http.HttpSession session) {
        try {
            session.invalidate();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
