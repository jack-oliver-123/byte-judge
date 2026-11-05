package com.jack.bytejudge.user.auth.interfaces.http;

import com.jack.bytejudge.common.core.response.ApiResponse;
import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.common.web.RequestLogFilter;
import com.jack.bytejudge.user.auth.application.port.AuthAudit;
import com.jack.bytejudge.user.auth.domain.AlreadyAuthenticatedException;
import com.jack.bytejudge.user.auth.domain.NotAuthenticatedException;
import com.jack.bytejudge.user.auth.domain.model.AuthAuditEntry;
import com.jack.bytejudge.user.auth.domain.model.AuthEvent;
import com.jack.bytejudge.user.auth.domain.model.AuthOutcome;
import com.jack.bytejudge.user.auth.interfaces.http.dto.LoginRequest;
import com.jack.bytejudge.user.auth.interfaces.http.vo.CsrfTokenView;
import com.jack.bytejudge.user.auth.interfaces.http.vo.IdentityView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final AuthenticationManager authenticator;
    private final SecurityContextRepository securityContextRepository;
    private final AuthAudit audit;
    private final ChangeSessionIdAuthenticationStrategy sessionRotation = new ChangeSessionIdAuthenticationStrategy();
    private final CsrfAuthenticationStrategy csrfRotation;
    private final CsrfLogoutHandler csrfLogout;
    private final SecurityContextLogoutHandler sessionLogout = new SecurityContextLogoutHandler();

    public AuthController(AuthenticationManager authenticator, CsrfTokenRepository csrfTokenRepository,
                          SecurityContextRepository securityContextRepository, AuthAudit audit) {
        this.authenticator = authenticator;
        this.securityContextRepository = securityContextRepository;
        this.audit = audit;
        this.csrfRotation = new CsrfAuthenticationStrategy(csrfTokenRepository);
        this.csrfRotation.setRequestHandler(new CsrfTokenRequestAttributeHandler());
        this.csrfLogout = new CsrfLogoutHandler(csrfTokenRepository);
    }

    @GetMapping("/api/v1/auth/csrf")
    ApiResponse<CsrfTokenView> csrf(CsrfToken token, HttpServletRequest request) {
        return ApiResponse.success(CsrfTokenView.of(token), RequestLogFilter.requestId(request));
    }

    @PostMapping(value = "/api/v1/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<IdentityView> login(@RequestBody byte[] rawBody, HttpServletRequest request,
                                    HttpServletResponse response) {
        LoginRequest body = LoginRequest.parse(rawBody);
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken)) {
            throw new AlreadyAuthenticatedException();
        }
        Authentication authentication = authenticator.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.account(), body.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        sessionRotation.onAuthentication(authentication, request, response);
        csrfRotation.onAuthentication(authentication, request, response);
        securityContextRepository.saveContext(context, request, response);
        var identity = (SessionIdentity) authentication.getPrincipal();
        return ApiResponse.success(IdentityView.of(identity.userId(), identity.account(), identity.role()),
                RequestLogFilter.requestId(request));
    }

    @GetMapping("/api/v1/users/me")
    ApiResponse<IdentityView> me(@AuthenticationPrincipal SessionIdentity identity,
                                 HttpServletRequest request) {
        if (identity == null) {
            throw new NotAuthenticatedException();
        }
        return ApiResponse.success(IdentityView.of(identity.userId(), identity.account(), identity.role()),
                RequestLogFilter.requestId(request));
    }

    /** Minimal administrator-only identity check; it does not add user management operations. */
    @GetMapping("/api/v1/admin/users/me")
    ApiResponse<IdentityView> adminMe(@AuthenticationPrincipal SessionIdentity identity,
                                      HttpServletRequest request) {
        if (identity == null) {
            throw new NotAuthenticatedException();
        }
        return ApiResponse.success(IdentityView.of(identity.userId(), identity.account(), identity.role()),
                RequestLogFilter.requestId(request));
    }

    @PostMapping("/api/v1/auth/logout")
    ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null || current instanceof AnonymousAuthenticationToken || !current.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
        var identity = (SessionIdentity) current.getPrincipal();
        audit.record(new AuthAuditEntry(AuthEvent.LOGOUT, AuthOutcome.SUCCESS, identity.userId(),
                identity.account(), null, RequestLogFilter.requestId(request)));
        // Clear the CSRF token first, then invalidate the session: Spring Session expires the cookie on
        // invalidation, and only the current session id is revoked.
        csrfLogout.logout(request, response, current);
        sessionLogout.logout(request, response, current);
        return ApiResponse.success(null, RequestLogFilter.requestId(request));
    }
}
