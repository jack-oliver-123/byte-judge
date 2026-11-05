package com.jack.bytejudge.gateway.config;

import com.jack.bytejudge.gateway.web.UntrustedHeaderFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class GatewayHeaderConfiguration {
    @Bean
    FilterRegistrationBean<UntrustedHeaderFilter> untrustedHeaderFilter() {
        var registration = new FilterRegistrationBean<>(new UntrustedHeaderFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
