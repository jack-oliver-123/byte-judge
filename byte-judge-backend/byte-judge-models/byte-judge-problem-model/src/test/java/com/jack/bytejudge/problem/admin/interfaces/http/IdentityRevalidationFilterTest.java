package com.jack.bytejudge.problem.admin.interfaces.http;

import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.problem.admin.application.port.IdentityVerifier;
import com.jack.bytejudge.problem.admin.domain.IdentityAccessDeniedException;
import com.jack.bytejudge.problem.audit.domain.model.ProblemAuditEntry;
import com.jack.bytejudge.problem.admin.domain.model.VerifiedIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityRevalidationFilterTest {
    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void authorityUnavailableNeverRunsProtectedWork() throws Exception {
        var request = authenticatedRequest();
        var chain = new MockFilterChain();
        var response = invoke(request, chain, (session, path, id) -> { throw new IllegalStateException(); });
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void forbiddenIdentityRetainsSessionAndReturns403() throws Exception {
        var request = authenticatedRequest();
        var session = (MockHttpSession) request.getSession(false);
        var chain = new MockFilterChain();
        var response = invoke(request, chain, (sid, path, id) -> { throw new IdentityAccessDeniedException(); });
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(session.isInvalid()).isFalse();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void revokedIdentityInvalidatesLocalSessionAndReturns401() throws Exception {
        var request = authenticatedRequest();
        var session = (MockHttpSession) request.getSession(false);
        var chain = new MockFilterChain();
        var response = invoke(request, chain, (sid, path, id) -> Optional.empty());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void currentRoleOverridesStaleAdministratorSnapshot() throws Exception {
        var chain = new MockFilterChain();
        var response = invoke(authenticatedRequest(), chain,
                (sid, path, id) -> Optional.of(new VerifiedIdentity(1L, "admin", "user")));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_user");
    }

    @Test
    void encodedAdministrativeRouteStillChecksAuthority() throws Exception {
        var request = authenticatedRequest();
        request.setRequestURI("/api/v1/%61dmin/problems/1");
        var chain = new MockFilterChain();
        var response = invoke(request, chain, (sid, path, id) -> {
            assertThat(path).isEqualTo("/api/v1/admin/problems/1");
            throw new IllegalStateException();
        });
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void differentVerifiedUserCannotReuseLoadedSessionIdentity() throws Exception {
        var chain = new MockFilterChain();
        var response = invoke(authenticatedRequest(), chain,
                (sid, path, id) -> Optional.of(new VerifiedIdentity(2L, "other", "admin")));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void verifiedAdministratorRunsWithFreshRequestContext() throws Exception {
        var request = authenticatedRequest();
        var snapshot = SecurityContextHolder.getContext();
        var chain = new MockFilterChain();
        var response = invoke(request, chain,
                (sid, path, id) -> Optional.of(new VerifiedIdentity(1L, "admin", "admin")));
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext()).isNotSameAs(snapshot);
    }

    private MockHttpServletRequest authenticatedRequest() {
        var request = new MockHttpServletRequest("GET", "/api/v1/admin/problems");
        request.getSession(true);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new SessionIdentity(1L, "admin", "admin", 0), null,
                List.of(new SimpleGrantedAuthority("ROLE_admin"))));
        SecurityContextHolder.setContext(context);
        return request;
    }

    private MockHttpServletResponse invoke(MockHttpServletRequest request, MockFilterChain chain,
                                           IdentityVerifier verifier) throws Exception {
        var response = new MockHttpServletResponse();
        List<ProblemAuditEntry> entries = new ArrayList<>();
        new IdentityRevalidationFilter(verifier, entries::add).doFilter(request, response, chain);
        return response;
    }
}
