package com.jack.bytejudge.problem.admin.infrastructure.client;

import com.jack.bytejudge.problem.admin.domain.IdentityAccessDeniedException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RestIdentityVerifierTest {
    @Test
    void authoritativeStatusDistinguishesRevokedForbiddenAndUnavailable() throws Exception {
        for (int status : new int[]{401, 403, 500, 503}) {
            var server = server(status, "{}");
            try {
                var verifier = verifier(server);
                if (status == 401) {
                    assertThat(verifier.verify("session", "/api/v1/admin/problems", "request")).isEmpty();
                } else if (status == 403) {
                    assertThatThrownBy(() -> verifier.verify("session", "/api/v1/admin/problems", "request"))
                            .isInstanceOf(IdentityAccessDeniedException.class);
                } else {
                    assertThatThrownBy(() -> verifier.verify("session", "/api/v1/admin/problems", "request"))
                            .isInstanceOf(IllegalStateException.class);
                }
            } finally { server.stop(0); }
        }
    }

    @Test
    void safeIdentityIsReadThroughTypedPrivateClient() throws Exception {
        var server = server(200, "{\"code\":0,\"data\":{\"userId\":\"123\",\"account\":\"admin\",\"role\":\"admin\"},\"requestId\":\"request\"}");
        try {
            var identity = verifier(server).verify("session", "/api/v1/admin/problems", "request").orElseThrow();
            assertThat(identity.userId()).isEqualTo(123L);
            assertThat(identity.role()).isEqualTo("admin");
        } finally { server.stop(0); }
    }

    @Test
    void malformedSuccessCannotAuthorizeRequest() throws Exception {
        var server = server(200, "{\"code\":0,\"data\":{\"userId\":\"1\",\"account\":\"admin\",\"role\":\"root\"}}");
        try {
            assertThatThrownBy(() -> verifier(server).verify("session", "/api/v1/admin/problems", "request"))
                    .isInstanceOf(IllegalStateException.class);
        } finally { server.stop(0); }
    }

    private RestIdentityVerifier verifier(HttpServer server) {
        var balancer = mock(LoadBalancerClient.class);
        when(balancer.choose("user")).thenReturn(new DefaultServiceInstance("one", "user", "127.0.0.1",
                server.getAddress().getPort(), false));
        return new RestIdentityVerifier(balancer, "user", "problem-test-credential-01234567890123456789",
                Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    private HttpServer server(int status, String body) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/identity/verify", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }
}
