package com.jack.bytejudge.gateway.identity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.PostExchange;

import java.net.URI;

/** Gateway-owned wire contract; no user-service classes cross the service boundary. */
public interface IdentityVerificationClient {
    @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    VerificationResponse verify(URI endpoint, @RequestHeader("X-Request-ID") String requestId,
                                @RequestBody VerificationRequest request);

    record VerificationRequest(String sessionId, String path) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VerificationResponse(int code, SafeIdentity data) { }

    record SafeIdentity(String userId, String account, String role) { }
}
