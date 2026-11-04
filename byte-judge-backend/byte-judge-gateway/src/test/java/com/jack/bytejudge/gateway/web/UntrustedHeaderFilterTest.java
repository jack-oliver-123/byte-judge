package com.jack.bytejudge.gateway.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class UntrustedHeaderFilterTest {
    @Test
    void forgedIdentityInternalAndForwardingHeadersNeverReachTheDownstreamRequest() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "999");
        request.addHeader("x-user-role", "admin");
        request.addHeader("X-Internal-Token", "forged");
        request.addHeader("X-Internal-Client-Address", "203.0.113.99");
        request.addHeader("Forwarded", "for=203.0.113.7");
        request.addHeader("X-Forwarded-For", "203.0.113.7");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("Cookie", "BJSESSION=session-value");
        request.addHeader("X-Request-ID", "trace-1");
        request.addHeader("Accept", "application/json");
        request.addHeader("X-Client-Trace", "keep-me");

        var chain = new MockFilterChain();
        new UntrustedHeaderFilter().doFilter(request, new MockHttpServletResponse(), chain);
        HttpServletRequest downstream = (HttpServletRequest) chain.getRequest();

        assertThat(downstream.getHeader("X-User-Id")).as("forged identity is stripped").isNull();
        assertThat(downstream.getHeader("x-user-role")).isNull();
        assertThat(downstream.getHeader("X-Internal-Token")).isNull();
        assertThat(downstream.getHeader("X-Internal-Client-Address"))
                .as("the gateway stamps its own trusted client address after this filter")
                .isNull();
        assertThat(downstream.getHeader("Forwarded")).isNull();
        assertThat(downstream.getHeader("X-Forwarded-For")).isNull();
        assertThat(downstream.getHeader("X-Forwarded-Proto")).isNull();
        assertThat(Collections.list(downstream.getHeaders("X-User-Id"))).isEmpty();
        assertThat(Collections.list(downstream.getHeaderNames()))
                .doesNotContain("X-User-Id", "x-user-role", "X-Internal-Token",
                        "Forwarded", "X-Forwarded-For", "X-Forwarded-Proto");
        assertThat(downstream.getIntHeader("X-User-Id")).isEqualTo(-1);
        assertThat(downstream.getDateHeader("X-Internal-Token")).isEqualTo(-1);

        assertThat(downstream.getHeader("Cookie")).as("the session cookie is forwarded untouched")
                .isEqualTo("BJSESSION=session-value");
        assertThat(downstream.getHeader("X-Request-ID")).isEqualTo("trace-1");
        assertThat(downstream.getHeader("Accept")).isEqualTo("application/json");
        assertThat(downstream.getHeader("X-Client-Trace")).isEqualTo("keep-me");
    }
}
