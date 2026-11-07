package com.jack.bytejudge.problem.admin.infrastructure.client;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Typed HTTP Service Client with explicit LoadBalancer resolution towards the user service's
 * private verify endpoint. Its private RestClient carries only this service's controlled
 * credential, never browser cookies or client identity headers. Verification answers map onto
 * the fail-closed port contract: revoked sessions, forbidden access and dependency failures
 * remain distinct decisions.
 */
public final class RestIdentityVerifier implements com.jack.bytejudge.problem.admin.application.port.IdentityVerifier {
    private static final String PATH = "/internal/v1/identity/verify";

    private final LoadBalancerClient loadBalancer;
    private final String serviceId;
    private final IdentityVerificationClient client;

    public RestIdentityVerifier(LoadBalancerClient loadBalancer, String serviceId, String credential,
                                Duration connectTimeout, Duration readTimeout) {
        if (credential == null || credential.length() < 32) {
            throw new IllegalStateException(
                    "byte-judge.identity.problem-credential must be at least 32 characters.");
        }
        if (connectTimeout.isZero() || connectTimeout.isNegative()
                || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalStateException("Identity verification timeouts must be positive.");
        }
        this.loadBalancer = loadBalancer;
        this.serviceId = serviceId;
        var http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        var restClient = RestClient.builder().requestFactory(factory)
                .defaultHeader("X-Internal-Service", "problem")
                .defaultHeader("X-Internal-Credential", credential)
                .build();
        this.client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build().createClient(IdentityVerificationClient.class);
    }

    @Override
    public java.util.Optional<com.jack.bytejudge.problem.admin.domain.model.VerifiedIdentity> verify(
            String sessionId, String path, String requestId) {
        ServiceInstance instance = loadBalancer.choose(serviceId);
        if (instance == null) {
            throw new IllegalStateException("No user service instance is available for identity verification.");
        }
        URI endpoint = instance.getUri().resolve(PATH);
        try {
            var response = client.verify(endpoint, requestId,
                    new IdentityVerificationClient.VerificationRequest(sessionId, path));
            if (response == null || response.code() != 0 || response.data() == null
                    || !("admin".equals(response.data().role()) || "user".equals(response.data().role()))) {
                throw new IllegalStateException("The identity verification response is invalid.");
            }
            long userId = Long.parseLong(response.data().userId());
            if (userId <= 0 || response.data().account() == null
                    || !response.data().account().matches("[a-z0-9_]{4,32}")) {
                throw new IllegalStateException("The identity verification response is invalid.");
            }
            return java.util.Optional.of(new com.jack.bytejudge.problem.admin.domain.model.VerifiedIdentity(
                    userId, response.data().account(), response.data().role()));
        } catch (RestClientResponseException rejected) {
            if (rejected.getStatusCode().value() == 401) {
                return java.util.Optional.empty();
            }
            if (rejected.getStatusCode().value() == 403) {
                throw new com.jack.bytejudge.problem.admin.domain.IdentityAccessDeniedException();
            }
            throw new IllegalStateException("Identity verification could not complete.");
        }
    }
}
