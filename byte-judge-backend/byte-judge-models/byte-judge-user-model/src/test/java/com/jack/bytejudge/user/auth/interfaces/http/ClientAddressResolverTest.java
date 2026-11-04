package com.jack.bytejudge.user.auth.interfaces.http;

import com.jack.bytejudge.common.web.ClientAddressHeader;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientAddressResolverTest {
    private final ClientAddressResolver resolver =
            new ClientAddressResolver("127.0.0.1,0:0:0:0:0:0:0:1");

    @Test
    void anUntrustedPeerCannotChooseItsOwnAddress() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.20.30.40");
        request.addHeader(ClientAddressHeader.NAME, "203.0.113.7");

        assertThat(resolver.resolve(request)).isEqualTo("10.20.30.40");
    }

    @Test
    void aTrustedProxySuppliesOneCanonicalAddress() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(ClientAddressHeader.NAME, "203.0.113.7");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void aTrustedProxyMaySupplyAnIpv6LiteralInAnyEquivalentForm() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("::1");
        request.addHeader(ClientAddressHeader.NAME, "::1");

        assertThat(resolver.resolve(request)).isEqualTo("0:0:0:0:0:0:0:1");
    }

    @Test
    void malformedMultipleOrMissingValuesFallBackToThePeer() {
        var multipleHeaders = new MockHttpServletRequest();
        multipleHeaders.setRemoteAddr("127.0.0.1");
        multipleHeaders.addHeader(ClientAddressHeader.NAME, "203.0.113.7");
        multipleHeaders.addHeader(ClientAddressHeader.NAME, "203.0.113.8");
        assertThat(resolver.resolve(multipleHeaders)).isEqualTo("127.0.0.1");

        var commaSeparated = new MockHttpServletRequest();
        commaSeparated.setRemoteAddr("127.0.0.1");
        commaSeparated.addHeader(ClientAddressHeader.NAME, "203.0.113.7, 203.0.113.8");
        assertThat(resolver.resolve(commaSeparated)).isEqualTo("127.0.0.1");

        var hostname = new MockHttpServletRequest();
        hostname.setRemoteAddr("127.0.0.1");
        hostname.addHeader(ClientAddressHeader.NAME, "attacker.example");
        assertThat(resolver.resolve(hostname)).isEqualTo("127.0.0.1");

        var missing = new MockHttpServletRequest();
        missing.setRemoteAddr("127.0.0.1");
        assertThat(resolver.resolve(missing)).isEqualTo("127.0.0.1");
    }

    @Test
    void invalidTrustedProxyConfigurationFailsAtStartup() {
        assertThatThrownBy(() -> new ClientAddressResolver("127.0.0.1,not-an-address"))
                .as("invalid trust configuration must not silently degrade")
                .isInstanceOf(IllegalArgumentException.class);
    }
}
