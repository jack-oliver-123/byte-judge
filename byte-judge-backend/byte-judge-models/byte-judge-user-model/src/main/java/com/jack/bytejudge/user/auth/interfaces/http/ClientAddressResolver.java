package com.jack.bytejudge.user.auth.interfaces.http;

import com.jack.bytejudge.common.web.ClientAddressHeader;
import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Resolves the address used as the login rate-limit source. Only a peer listed as a trusted
 * proxy may supply {@link ClientAddressHeader#NAME}; the value must be exactly one IP literal.
 * Any malformed, repeated or comma-separated value falls back to the socket peer, so a client
 * cannot select its own bucket. A direct client that is not a trusted proxy is always keyed by
 * its socket address, regardless of headers.
 */
public final class ClientAddressResolver {
    private static final Pattern IPV4 = Pattern.compile("(?:\\d{1,3}\\.){3}\\d{1,3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.%]+");
    private static final String UNKNOWN = "unknown";

    private final Set<String> trustedProxies;

    public ClientAddressResolver(String trustedProxies) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(value -> canonicalAddress(value).orElseThrow(
                        () -> new IllegalArgumentException("Invalid trusted proxy address: " + value)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (this.trustedProxies.isEmpty()) {
            throw new IllegalArgumentException("At least one trusted proxy address is required");
        }
    }

    public String resolve(HttpServletRequest request) {
        String peer = canonicalAddress(request.getRemoteAddr()).orElse(UNKNOWN);
        if (!trustedProxies.contains(peer)) {
            return peer;
        }
        List<String> values = Collections.list(request.getHeaders(ClientAddressHeader.NAME));
        if (values.size() != 1) {
            return peer;
        }
        String supplied = values.getFirst().strip();
        if (supplied.indexOf(',') >= 0) {
            return peer;
        }
        return canonicalAddress(supplied).orElse(peer);
    }

    private static Optional<String> canonicalAddress(String value) {
        if (value == null
                || (!IPV4.matcher(value).matches() && !(value.indexOf(':') >= 0 && IPV6.matcher(value).matches()))) {
            return Optional.empty();
        }
        try {
            // Only literals reach this point: IPv4 is digits/dots and IPv6 contains a colon, so no
            // DNS lookup is possible.
            return Optional.of(InetAddress.getByName(value).getHostAddress());
        } catch (UnknownHostException invalidLiteral) {
            return Optional.empty();
        }
    }
}
