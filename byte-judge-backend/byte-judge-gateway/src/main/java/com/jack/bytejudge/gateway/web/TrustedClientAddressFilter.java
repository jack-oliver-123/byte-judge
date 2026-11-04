package com.jack.bytejudge.gateway.web;

import com.jack.bytejudge.common.web.ClientAddressHeader;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Stamps the address of the socket peer on the downstream request. It runs after
 * {@link UntrustedHeaderFilter}, so a client-supplied value under the same name is already hidden
 * and this wrapper replaces it rather than appending. The gateway reads only the socket peer, never
 * the client's forwarding headers.
 */
public final class TrustedClientAddressFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String address = request.getRemoteAddr();
        if (address == null || address.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new ClientAddressRequest(request, address), response);
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    private static boolean isAddressHeader(String name) {
        return ClientAddressHeader.NAME.equalsIgnoreCase(name);
    }

    private static final class ClientAddressRequest extends HttpServletRequestWrapper {
        private final String address;

        private ClientAddressRequest(HttpServletRequest request, String address) {
            super(request);
            this.address = address;
        }

        @Override
        public String getHeader(String name) {
            return isAddressHeader(name) ? address : super.getHeader(name);
        }

        @Override
        public java.util.Enumeration<String> getHeaders(String name) {
            return isAddressHeader(name) ? Collections.enumeration(List.of(address)) : super.getHeaders(name);
        }

        @Override
        public java.util.Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.removeIf(TrustedClientAddressFilter::isAddressHeader);
            names.add(ClientAddressHeader.NAME);
            return Collections.enumeration(names);
        }

        @Override
        public int getIntHeader(String name) {
            return isAddressHeader(name) ? -1 : super.getIntHeader(name);
        }

        @Override
        public long getDateHeader(String name) {
            return isAddressHeader(name) ? -1 : super.getDateHeader(name);
        }
    }
}
