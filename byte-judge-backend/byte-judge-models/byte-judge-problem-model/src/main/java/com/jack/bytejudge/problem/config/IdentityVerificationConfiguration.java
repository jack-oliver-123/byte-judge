package com.jack.bytejudge.problem.config;

import com.jack.bytejudge.problem.admin.application.port.IdentityVerifier;
import com.jack.bytejudge.problem.admin.infrastructure.client.RestIdentityVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Remote identity verification wiring for the problem service. Protected requests are checked
 * against the user service's authoritative storage through the private verify endpoint; there is
 * no positive cache and no cross-database read.
 */
@Configuration(proxyBeanMethods = false)
public class IdentityVerificationConfiguration {

    @Bean
    IdentityVerifier identityVerifier(
            org.springframework.cloud.client.loadbalancer.LoadBalancerClient loadBalancer,
            @Value("${byte-judge.identity.user-service-id:byte-judge-user-model}") String serviceId,
            @Value("${byte-judge.identity.problem-credential}") String credential,
            @Value("${byte-judge.identity.connect-timeout:1s}") Duration connectTimeout,
            @Value("${byte-judge.identity.read-timeout:2s}") Duration readTimeout) {
        return new RestIdentityVerifier(loadBalancer, serviceId, credential, connectTimeout, readTimeout);
    }
}
