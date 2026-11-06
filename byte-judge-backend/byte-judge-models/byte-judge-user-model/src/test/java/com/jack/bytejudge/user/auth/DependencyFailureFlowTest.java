package com.jack.bytejudge.user.auth;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.ServerSocket;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP acceptance for dependency failures. Disposable MySQL, Redis and Nacos containers are
 * paused instead of deleted, and every assertion goes through the gateway unless the user service
 * itself must be proven to fail closed.
 */
class DependencyFailureFlowTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static AuthenticationFlowTest.AuthenticationSystem system;

    @BeforeAll
    static void startSystem() throws Exception {
        int deadDownstreamPort;
        try (var port = new ServerSocket(0)) {
            deadDownstreamPort = port.getLocalPort();
        }
        // A short Redis timeout keeps a paused container observable as a failure. The simple
        // discovery instance points the otherwise unused problem route at a closed local port, so
        // a connection-refused path is exercised without registering a fake Nacos instance.
        String simpleDiscovery = "{\"spring\":{\"cloud\":{\"discovery\":{\"client\":{\"simple\":{\"instances\":"
                + "{\"byte-judge-problem-model\":[{\"uri\":\"http://127.0.0.1:" + deadDownstreamPort + "\"}]}}}}}}}";
        system = new AuthenticationFlowTest.AuthenticationSystem(Map.of(
                "SPRING_DATA_REDIS_CONNECT_TIMEOUT", "1s",
                "SPRING_DATA_REDIS_TIMEOUT", "1s",
                "SPRING_APPLICATION_JSON", simpleDiscovery));
        system.start();
    }

    @AfterAll
    static void stopSystem() throws Exception {
        if (system != null) {
            system.close();
        }
    }

    @Test
    void redisOutageFailsClosedAndRecoveryKeepsRevokedSessionsRejected() throws Exception {
        var administrator = system.newBrowser();
        assertThat(system.login(administrator, system.csrf(administrator), system.administrator)
                .response().statusCode()).isEqualTo(200);
        assertThat(system.get(administrator, "/api/v1/users/me").statusCode()).isEqualTo(200);
        String administratorSession = administrator.cookie("BJSESSION");

        var loggedOut = system.newBrowser();
        assertThat(system.login(loggedOut, system.csrf(loggedOut), system.administrator)
                .response().statusCode()).isEqualTo(200);
        String loggedOutSession = loggedOut.cookie("BJSESSION");
        assertThat(system.logout(loggedOut, system.csrf(loggedOut)).statusCode()).isEqualTo(200);

        // Prepared before the outage: the login request can reach the credential check only if the
        // session and rate-limit lookups are not skipped.
        var blockedLogin = system.newBrowser();
        var blockedCsrf = system.csrf(blockedLogin);

        system.pauseRedis();
        try {
            assertErrorContract(system.getNoWait(administrator, "/api/v1/users/me"), 503, 50300);
            assertErrorContract(system.directGetOn(system.primaryUserPort(),
                    system.browserWithSession(administratorSession), "/api/v1/users/me", Map.of()),
                    503, 50300);
            assertErrorContract(system.loginRaw(blockedLogin, blockedCsrf, system.administrator, Map.of()),
                    503, 50300);
            var fresh = system.newBrowser();
            var csrf = fresh.perform(HttpRequest.newBuilder(system.gatewayUri("/api/v1/auth/csrf"))
                    .timeout(Duration.ofSeconds(10)).GET());
            assertErrorContract(csrf, 503, 50300);
            assertThat(fresh.hasCookie("BJSESSION"))
                    .as("a failed session save must not hand out a usable session cookie").isFalse();
        } finally {
            system.resumeRedis();
        }

        system.awaitGatewayStatus(blockedLogin, "/api/v1/users/me", 401, Duration.ofSeconds(60));
        assertThat(system.get(administrator, "/api/v1/users/me").statusCode())
                .as("pausing Redis keeps its data; the untouched session is valid again")
                .isEqualTo(200);
        assertErrorContract(system.getNoWait(system.browserWithSession(loggedOutSession),
                "/api/v1/users/me"), 401, 40100);
    }

    @Test
    void identityVerificationTimeoutIsBoundedAndFailsClosed() throws Exception {
        var browser = system.newBrowser();
        assertThat(system.login(browser, system.csrf(browser), system.administrator)
                .response().statusCode()).isEqualTo(200);
        assertThat(system.get(browser, "/api/v1/users/me").statusCode()).isEqualTo(200);

        // Far beyond the gateway's 2s read timeout, so the assertion distinguishes the configured
        // bound from simply waiting for the downstream to finish.
        system.setInternalVerificationDelay(Duration.ofSeconds(15));
        try {
            long started = System.nanoTime();
            var response = system.getNoWait(browser, "/api/v1/users/me");
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            assertErrorContract(response, 503, 50300);
            assertThat(elapsedMillis).as("the configured read timeout, not the downstream delay, bounds the wait")
                    .isLessThan(10_000);
            String requestId = response.headers().firstValue("X-Request-ID").orElseThrow();
            system.awaitGatewayApplicationLog("identity verification failed requestId=" + requestId);
        } finally {
            system.clearInternalVerificationDelay();
        }
        system.awaitGatewayStatus(browser, "/api/v1/users/me", 200, Duration.ofSeconds(30));
    }

    @Test
    void absentOrUnreachableDownstreamsUseTheDependencyErrorContract() throws Exception {
        assertErrorContract(system.gatewayGet("/api/v1/contests/probe"), 503, 50300);
        var refused = system.gatewayGet("/api/v1/problems/probe");
        assertErrorContract(refused, 503, 50300);
        String requestId = refused.headers().firstValue("X-Request-ID").orElseThrow();
        system.awaitGatewayApplicationLog("http requestId=" + requestId);
    }

    @Test
    void authoritativeStorageOutageFailsClosedAndRecovers() throws Exception {
        var browser = system.newBrowser();
        assertThat(system.login(browser, system.csrf(browser), system.administrator)
                .response().statusCode()).isEqualTo(200);
        assertThat(system.get(browser, "/api/v1/users/me").statusCode()).isEqualTo(200);
        var login = system.newBrowser();
        var loginCsrf = system.csrf(login);

        system.pauseDatabase();
        try {
            assertErrorContract(system.getNoWait(browser, "/api/v1/users/me"), 503, 50300);
            assertErrorContract(system.directGetOn(system.primaryUserPort(), browser,
                    "/api/v1/users/me", Map.of()), 503, 50300);
            assertErrorContract(system.loginRaw(login, loginCsrf, system.administrator, Map.of()),
                    503, 50300);
        } finally {
            system.resumeDatabase();
        }
        system.awaitGatewayStatus(browser, "/api/v1/users/me", 200, Duration.ofSeconds(60));
    }

    @Test
    void everyErrorOutletUsesTheResponseContractAndCorrelatesRequests() throws Exception {
        var administrator = system.newBrowser();
        assertThat(system.login(administrator, system.csrf(administrator), system.administrator)
                .response().statusCode()).isEqualTo(200);
        var guest = system.newBrowser();
        var guestCsrf = system.csrf(guest);

        assertErrorContract(system.post(guest, "/api/v1/auth/login", "application/json",
                guestCsrf.headerName(), guestCsrf.token(), "{not-json"), 400, 40000);
        assertErrorContract(system.getNoWait(system.newBrowser(), "/api/v1/users/me"), 401, 40100);
        assertErrorContract(system.post(guest, "/api/v1/auth/login", "application/json", null, null,
                        JSON.writeValueAsString(Map.of("account", "contract_user", "password", "not-a-secret"))),
                403, 40301);

        var ordinary = system.newBrowser();
        assertThat(system.login(ordinary, system.csrf(ordinary), system.ordinaryUser)
                .response().statusCode()).isEqualTo(200);
        assertErrorContract(system.getNoWait(ordinary, "/api/v1/admin/users/me"), 403, 40300);

        assertErrorContract(system.get(guest, "/api/v1/auth/nothing-here"), 404, 40400);
        assertErrorContract(system.gatewayGet("/api/v1/nothing-here"), 404, 40400);
        assertErrorContract(system.get(guest, "/api/v1/auth/login"), 405, 40500);
        assertErrorContract(system.login(administrator, system.csrf(administrator), system.administrator)
                .response(), 409, 40900);
        assertErrorContract(system.post(guest, "/api/v1/auth/login", "text/plain",
                guestCsrf.headerName(), guestCsrf.token(), "plain"), 415, 41500);
        assertErrorContract(system.getWithHeaders(guest, "/api/v1/auth/login",
                Map.of("X-Auth12-Fault", "explode")), 500, 50000);
        assertErrorContract(system.gatewayGet("/api/v1/contests/probe"), 503, 50300);

        var limited = system.newBrowserAt("127.0.0.4");
        var limitedCsrf = system.csrf(limited);
        String account = "rate_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var wrong = new AuthenticationFlowTest.AuthenticationSystem.FixtureUser(
                account, "wrong-secret", "user", "normal");
        HttpResponse<String> last = null;
        for (int attempt = 1; attempt <= 11; attempt++) {
            last = system.loginRaw(limited, limitedCsrf, wrong, Map.of());
            if (attempt <= 10) {
                assertThat(last.statusCode()).as("attempt %d stays a credential failure", attempt)
                        .isEqualTo(401);
            }
        }
        assertErrorContract(last, 429, 42900);
    }

    private static void assertErrorContract(HttpResponse<String> response, int status, int code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        String requestId = response.headers().firstValue("X-Request-ID").orElseThrow();
        assertThat(requestId).isNotBlank().matches("[A-Za-z0-9_-]{1,64}");
        var body = JSON.readTree(response.body());
        assertThat(JSON.readValue(response.body(), Map.class).keySet())
                .containsExactlyInAnyOrder("code", "data", "message", "requestId", "serverTime");
        assertThat(body.path("code").asInt()).isEqualTo(code);
        assertThat(body.path("requestId").asText()).isEqualTo(requestId);
        assertThat(body.path("message").asText()).isNotBlank();
        assertThat(body.path("serverTime").asText())
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
        if (code == 42900) {
            assertThat(body.path("data").path("retryAfterSeconds").asLong()).isPositive();
        } else {
            assertThat(body.path("data").isNull()).isTrue();
        }
        String text = response.body().toLowerCase(Locale.ROOT);
        assertThat(text).doesNotContain("exception", "stacktrace", "jdbc", "select ", "insert ",
                "$argon2", "bjsession=", "password=");
    }
}
