package com.jack.bytejudge.common.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.annotation.Value;

@Configuration(proxyBeanMethods = false)
public class WebProtocolConfiguration {
    @Bean
    FilterRegistrationBean<RequestLogFilter> requestLogFilter(
            @Value("${byte-judge.web.ingress:false}") boolean ingress) {
        var bean = new FilterRegistrationBean<>(new RequestLogFilter(ingress));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
