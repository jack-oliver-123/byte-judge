package com.jack.bytejudge.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class WebProtocolConfiguration {
    private static final Logger log = LoggerFactory.getLogger(WebProtocolConfiguration.class);

    @Bean
    FilterRegistrationBean<RequestLogFilter> requestLogFilter(
            // Deliberately no default: the ingress flag decides whether a client-supplied
            // correlation id may be trusted. A missing value must stop startup rather than
            // silently pick the insecure interpretation.
            @Value("${byte-judge.web.ingress}") boolean ingress) {
        log.info("request correlation configured ingress={} header={}", ingress, RequestLogFilter.HEADER);
        var bean = new FilterRegistrationBean<>(new RequestLogFilter(ingress));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
