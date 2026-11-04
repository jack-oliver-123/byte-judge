package com.jack.bytejudge.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration(proxyBeanMethods = false)
public class GatewayHttpClientConfiguration {
    /**
     * The proxy must forward exactly the headers of the current browser request. The JDK client
     * keeps no cookie jar, so a Set-Cookie from one downstream response can never leak into the
     * next browser's downstream request.
     */
    @Bean
    ClientHttpRequestFactory gatewayClientHttpRequestFactory() {
        return new JdkClientHttpRequestFactory();
    }
}
