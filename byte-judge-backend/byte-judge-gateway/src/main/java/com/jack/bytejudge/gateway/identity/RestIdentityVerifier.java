package com.jack.bytejudge.gateway.identity;

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
 * Uses a typed HTTP Service Client with explicit LoadBalancer resolution. Its private RestClient
 * carries only the gateway service credential, never browser cookies or client identity headers.
 * Keeping this client isolated avoids resolving the gateway's own proxy routes twice.
 */
public final class RestIdentityVerifier implements IdentityVerifier {
    private static final String PATH = "/internal/v1/identity/verify";

    private final LoadBalancerClient loadBalancer;
    private final String serviceId;
    private final IdentityVerificationClient client;

    public RestIdentityVerifier(LoadBalancerClient loadBalancer, String serviceId, String credential,
                                Duration connectTimeout, Duration readTimeout) {
        if (credential == null || credential.length() < 32) {
            throw new IllegalStateException(
                    "byte-judge.internal.gateway-credential must be at least 32 characters.");
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
                .defaultHeader("X-Internal-Service", "gateway")
                .defaultHeader("X-Internal-Credential", credential)
                .build();
        this.client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build().createClient(IdentityVerificationClient.class);
    }

    @Override
    public VerificationResult verify(String sessionId, String path, String requestId) {
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
            return new VerificationResult(200, response.data().role());
        } catch (RestClientResponseException rejected) {
            return new VerificationResult(rejected.getStatusCode().value(), null);
        }
    }
}
