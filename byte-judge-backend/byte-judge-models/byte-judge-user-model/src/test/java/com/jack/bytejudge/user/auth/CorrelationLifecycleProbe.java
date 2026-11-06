package com.jack.bytejudge.user.auth;

import com.jack.bytejudge.common.web.RequestLogFilter;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Observes the real servlet lifecycle after all filters, and is absent from the shipped JAR. */
@Configuration(proxyBeanMethods = false)
@Profile("auth-http-test")
class CorrelationLifecycleProbe {
    @Bean
    ServletListenerRegistrationBean<ServletRequestListener> correlationLifecycleListener() {
        return new ServletListenerRegistrationBean<>(new ServletRequestListener() {
            @Override
            public void requestDestroyed(ServletRequestEvent event) {
                Object requestId = event.getServletRequest().getAttribute(RequestLogFilter.REQUEST_ID);
                if (requestId != null) {
                    String remaining = MDC.get(RequestLogFilter.MDC_KEY);
                    LoggerFactory.getLogger("bytejudge.test.lifecycle").info(
                            "request completed completedRequestId={} remainingMdc={}",
                            requestId, remaining == null ? "empty" : remaining);
                }
            }
        });
    }
}
