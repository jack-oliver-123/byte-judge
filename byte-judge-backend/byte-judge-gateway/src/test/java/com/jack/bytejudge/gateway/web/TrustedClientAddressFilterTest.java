package com.jack.bytejudge.gateway.web;

import com.jack.bytejudge.common.web.ClientAddressHeader;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedClientAddressFilterTest {
    @Test
    void theSocketPeerOverridesAnyClientSuppliedAddressExactlyOnce() throws Exception {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.50");
        request.addHeader(ClientAddressHeader.NAME, "198.51.100.1");
        request.addHeader("Accept", "application/json");

        var chain = new MockFilterChain();
        new TrustedClientAddressFilter().doFilter(request, new MockHttpServletResponse(), chain);
        HttpServletRequest downstream = (HttpServletRequest) chain.getRequest();

        assertThat(downstream.getHeader(ClientAddressHeader.NAME)).isEqualTo("203.0.113.50");
        assertThat(Collections.list(downstream.getHeaders(ClientAddressHeader.NAME)))
                .containsExactly("203.0.113.50");
        assertThat(Collections.list(downstream.getHeaderNames()).stream()
                .filter(ClientAddressHeader.NAME::equalsIgnoreCase).count())
                .as("the header must be present exactly once")
                .isEqualTo(1);
        assertThat(downstream.getHeader("Accept")).isEqualTo("application/json");
    }
}
