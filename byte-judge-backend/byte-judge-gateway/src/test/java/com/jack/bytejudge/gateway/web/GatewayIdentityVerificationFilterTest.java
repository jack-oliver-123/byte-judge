package com.jack.bytejudge.gateway.web;

import com.jack.bytejudge.gateway.identity.IdentityVerifier;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayIdentityVerificationFilterTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Cookie SESSION = new Cookie("BJSESSION", "session-id");

    @Test
    void protectedRequestWithoutSessionIsRejectedBeforeAnyDownstreamWork() throws Exception {
        var downstream = new MockFilterChain();
        var response = invoke(new MockHttpServletRequest(), downstream, (session, path, request) -> {
            throw new AssertionError("verification must not be attempted without a session");
        }, "/api/v1/admin/users/me");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(JSON.readTree(response.getContentAsString()).path("code").asInt(-1)).isEqualTo(40100);
        assertThat(downstream.getRequest()).isNull();
    }

    @Test
    void nonAdministratorIsRejectedAtTheAdministrativeEntry() throws Exception {
        var downstream = new MockFilterChain();
        var response = invoke(requestWithSession(), downstream,
                (session, path, request) -> new IdentityVerifier.VerificationResult(200, "user"),
                "/api/v1/admin/users/me");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(JSON.readTree(response.getContentAsString()).path("code").asInt(-1)).isEqualTo(40300);
        assertThat(downstream.getRequest()).isNull();
    }

    @Test
    void administratorContinuesToTheProtectedRoute() throws Exception {
        var downstream = new MockFilterChain();
        var response = invoke(requestWithSession(), downstream,
                (session, path, request) -> new IdentityVerifier.VerificationResult(200, "admin"),
                "/api/v1/admin/users/me");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(downstream.getRequest()).isNotNull();
    }

    @Test
    void ordinaryIdentityIsAllowedOnANonAdministrativeProtectedPath() throws Exception {
        var downstream = new MockFilterChain();
        var response = invoke(requestWithSession(), downstream,
                (session, path, request) -> new IdentityVerifier.VerificationResult(200, "user"),
                "/api/v1/users/me");

        assertThat(downstream.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void unavailableAuthorityFailsClosedWithoutRunningProtectedWork() throws Exception {
        var downstream = new MockFilterChain();
        var response = invoke(requestWithSession(), downstream,
                (session, path, request) -> {
                    throw new IllegalStateException("connection refused");
                },
                "/api/v1/users/me");

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(JSON.readTree(response.getContentAsString()).path("code").asInt(-1)).isEqualTo(50300);
        assertThat(downstream.getRequest()).isNull();
    }

    @Test
    void userServiceDecisionIsRelayed() throws Exception {
        for (int status : new int[]{401, 403}) {
            var downstream = new MockFilterChain();
            var response = invoke(requestWithSession(), downstream,
                    (session, path, request) -> new IdentityVerifier.VerificationResult(status, null),
                    "/api/v1/users/me");
            assertThat(response.getStatus()).isEqualTo(status);
            assertThat(downstream.getRequest()).isNull();
        }
    }

    @Test
    void percentEncodedProtectedPathsCannotSkipVerification() throws Exception {
        for (String path : new String[]{"/api/v1/%61dmin/users/me", "/api/v1/users/%6de",
                "/internal/%76%31/identity/verify"}) {
            var downstream = new MockFilterChain();
            var response = invoke(requestWithSession(), downstream,
                    (session, verified, request) -> {
                        assertThat(verified).as("verification receives the decoded routing path")
                                .doesNotContain("%");
                        throw new IllegalStateException("authority unavailable");
                    }, path);
            assertThat(response.getStatus()).as(path).isIn(404, 503);
            assertThat(downstream.getRequest()).as(path).isNull();
        }
    }

    @Test
    void internalAndUnmappedPathsAreNotForwardedByThePublicEntry() throws Exception {
        var downstream = new MockFilterChain();
        var response = invoke(new MockHttpServletRequest(), downstream,
                (session, path, request) -> {
                    throw new AssertionError("internal paths must not be verified or proxied");
                }, "/internal/v1/identity/verify");

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(JSON.readTree(response.getContentAsString()).path("code").asInt(-1)).isEqualTo(40400);
        assertThat(downstream.getRequest()).isNull();
    }

    private MockHttpServletResponse invoke(MockHttpServletRequest request, MockFilterChain downstream,
                                           IdentityVerifier verifier, String path) throws Exception {
        request.setRequestURI(path);
        request.setServletPath(path);
        var response = new MockHttpServletResponse();
        new GatewayIdentityVerificationFilter(verifier).doFilter(request, response, downstream);
        return response;
    }

    private MockHttpServletRequest requestWithSession() {
        var request = new MockHttpServletRequest();
        request.setCookies(SESSION);
        return request;
    }
}
