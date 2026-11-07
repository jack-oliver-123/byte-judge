package com.jack.bytejudge.user.auth.interfaces.internal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class InternalCallGuardTest {
    private static final String GATEWAY = "gateway-test-credential-0123456789012345";
    private static final String PROBLEM = "problem-test-credential-0123456789012345";
    private final InternalCallGuard guard = new InternalCallGuard(Map.of("gateway", GATEWAY, "problem", PROBLEM));

    @Test
    void eachCallerMustPresentItsOwnCredential() {
        assertThat(guard.accepts(request("gateway", GATEWAY))).isTrue();
        assertThat(guard.accepts(request("problem", PROBLEM))).isTrue();
        assertThat(guard.accepts(request("gateway", PROBLEM))).isFalse();
        assertThat(guard.accepts(request("problem", GATEWAY))).isFalse();
        assertThat(guard.accepts(request("unknown", PROBLEM))).isFalse();
    }

    @Test
    void missingOrBrowserIdentityHeadersCannotAuthenticateService() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-User-Role", "admin");
        assertThat(guard.accepts(request)).isFalse();
        request.addHeader("X-Internal-Service", "problem");
        assertThat(guard.accepts(request)).isFalse();
    }

    @Test
    void weakCredentialFailsAtStartup() {
        assertThatThrownBy(() -> new InternalCallGuard(Map.of("problem", "short")))
                .isInstanceOf(IllegalStateException.class);
    }

    private MockHttpServletRequest request(String caller, String credential) {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service", caller);
        request.addHeader("X-Internal-Credential", credential);
        return request;
    }
}
