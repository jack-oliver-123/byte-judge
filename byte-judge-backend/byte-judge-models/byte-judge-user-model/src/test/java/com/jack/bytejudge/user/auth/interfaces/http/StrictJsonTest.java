package com.jack.bytejudge.user.auth.interfaces.http;

import com.jack.bytejudge.user.auth.interfaces.http.dto.LoginRequest;
import com.jack.bytejudge.user.auth.interfaces.internal.IdentityVerificationRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The strict reader is the single definition for authentication protocol bodies; both the browser
 * login body and the internal identity verification body must obey it through their own parse
 * rules. The CLI credential reader is a separate contract covered by AdminInitializationTest.
 */
class StrictJsonTest {

    private static byte[] body(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void aWellFormedLoginBodyParsesIntoItsTwoFields() {
        LoginRequest request = LoginRequest.parse(body(
                "{\"account\":\"alice\",\"password\":\"pAss wörd\"}"));

        assertThat(request.account()).isEqualTo("alice");
        assertThat(request.password()).isEqualTo("pAss wörd");
    }

    @Test
    void aWellFormedVerificationBodyParsesIntoItsTwoFields() {
        IdentityVerificationRequest request = IdentityVerificationRequest.parse(body(
                "{\"sessionId\":\"s-1\",\"path\":\"/api/v1/users/me\"}"));

        assertThat(request.sessionId()).isEqualTo("s-1");
        assertThat(request.path()).isEqualTo("/api/v1/users/me");
    }

    @Test
    void loginBodiesRejectEveryStrictnessViolation() {
        String password = "\"password\":\"secret\"";
        for (String json : new String[]{
                "{\"account\":\"a\"," + password + ",\"role\":\"admin\"}",   // unknown field
                "{\"account\":\"a\",\"account\":\"a\"," + password + "}",    // duplicate key
                "{\"account\":\"a\"," + password + "} {}",                   // trailing content
                "{\"account\":12345," + password + "}",                      // integer coercion
                "{\"account\":1.5," + password + "}",                        // float coercion
                "{\"account\":true," + password + "}",                       // boolean coercion
                "{\"account\":[\"a\"]," + password + "}",                    // array coercion
                "{\"account\":{\"v\":\"a\"}," + password + "}",              // object coercion
                "null",                                                      // null document
                "{\"account\":\"a\"}",                                       // missing password
                "",                                                          // empty body
                "{\"account\":null," + password + "}"}) {                    // null field value
            assertThatThrownBy(() -> LoginRequest.parse(body(json)))
                    .as("login body: %s", json)
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    @Test
    void verificationBodiesRejectEveryStrictnessViolation() {
        String session = "\"sessionId\":\"s-1\"";
        for (String json : new String[]{
                "{" + session + ",\"path\":\"/p\",\"userId\":\"9\"}",        // unknown field
                "{" + session + "," + session + ",\"path\":\"/p\"}",         // duplicate key
                "{" + session + ",\"path\":\"/p\"} null",                    // trailing content
                "{\"sessionId\":12345,\"path\":\"/p\"}",                     // integer coercion
                "{\"sessionId\":[\"s\"],\"path\":\"/p\"}",                   // array coercion
                "null",                                                      // null document
                "{\"path\":\"/p\"}",                                         // missing session
                "{\"sessionId\":\"s-1\"}",                                   // missing path
                "{\"sessionId\":\"s-1\",\"path\":null}",                     // null path value
                "{\"sessionId\":\" \",\"path\":\"/p\"}",                     // blank session
                "{\"sessionId\":\"s-1\",\"path\":\"api/v1/x\"}",             // path without slash
                "{\"sessionId\":\"" + "s".repeat(257) + "\",\"path\":\"/p\"}",   // session too long
                "{\"sessionId\":\"s-1\",\"path\":\"/" + "p".repeat(256) + "\"}"}) {  // path too long
            assertThatThrownBy(() -> IdentityVerificationRequest.parse(body(json)))
                    .as("verification body: %s", json)
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    @Test
    void fieldBoundariesAcceptExactlyAtTheLimits() {
        IdentityVerificationRequest atLimits = IdentityVerificationRequest.parse(body(
                "{\"sessionId\":\"" + "s".repeat(256) + "\",\"path\":\"/" + "p".repeat(255) + "\"}"));

        assertThat(atLimits.sessionId()).hasSize(256);
        assertThat(atLimits.path()).hasSize(256);
    }
}
