package com.jack.bytejudge.gateway.identity;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Calls the private identity endpoint through service discovery. The gateway's dedicated service
 * credential is attached only to this call; the browser's cookies and any identity header are never
 * forwarded to it.
 *
 * <p>The instance is resolved explicitly rather than through a shared load-balanced
 * {@code RestClient.Builder}: the gateway's own proxy routes must keep their own load-balancer
 * resolution, and reusing one builder makes both layers resolve the instance twice.
 */
public final class RestIdentityVerifier implements IdentityVerifier {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PATH = "/internal/v1/identity/verify";

    private final LoadBalancerClient loadBalancer;
    private final String serviceId;
    private final String credential;
    private final RestClient client;

    public RestIdentityVerifier(LoadBalancerClient loadBalancer, String serviceId, String credential) {
        if (credential == null || credential.length() < 32) {
            throw new IllegalStateException(
                    "byte-judge.internal.gateway-credential must be at least 32 characters.");
        }
        this.loadBalancer = loadBalancer;
        this.serviceId = serviceId;
        this.credential = credential;
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public VerificationResult verify(String sessionId, String path, String requestId) {
        ServiceInstance instance = loadBalancer.choose(serviceId);
        if (instance == null) {
            throw new IllegalStateException("No user service instance is available for identity verification.");
        }
        URI endpoint = instance.getUri().resolve(PATH);
        return client.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Service", "gateway")
                .header("X-Internal-Credential", credential)
                .header("X-Request-ID", requestId)
                .body(Map.of("sessionId", sessionId, "path", path))
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status != 200) {
                        return new VerificationResult(status, null);
                    }
                    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    String role = JSON.readTree(body).path("data").path("role").asText("");
                    return new VerificationResult(200, role);
                });
    }
}
