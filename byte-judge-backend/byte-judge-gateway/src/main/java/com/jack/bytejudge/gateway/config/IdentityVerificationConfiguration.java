package com.jack.bytejudge.gateway.config;

import com.jack.bytejudge.gateway.identity.IdentityVerifier;
import com.jack.bytejudge.gateway.identity.RestIdentityVerifier;
import com.jack.bytejudge.gateway.web.GatewayIdentityVerificationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class IdentityVerificationConfiguration {
    @Bean
    IdentityVerifier identityVerifier(LoadBalancerClient loadBalancerClient,
                                      @Value("${byte-judge.internal.gateway-credential}") String gatewayCredential) {
        return new RestIdentityVerifier(loadBalancerClient, "byte-judge-user-model", gatewayCredential);
    }

    @Bean
    FilterRegistrationBean<GatewayIdentityVerificationFilter> gatewayIdentityVerificationFilter(
            IdentityVerifier verifier) {
        var registration = new FilterRegistrationBean<>(new GatewayIdentityVerificationFilter(verifier));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
