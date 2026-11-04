package com.jack.bytejudge.common.web;

/**
 * Internal header written by the gateway after it strips every client-supplied identity and
 * forwarding header. Downstream services read it only when the immediate peer is a configured
 * trusted proxy.
 */
public final class ClientAddressHeader {
    public static final String NAME = "X-Internal-Client-Address";

    private ClientAddressHeader() { }
}
