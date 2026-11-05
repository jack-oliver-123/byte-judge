package com.jack.bytejudge.user.auth;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Fault injection targets disposable Nacos and independent service processes, never local data. */
class NacosRuntimeFlowTest {
    private static AuthenticationFlowTest.AuthenticationSystem system;

    @BeforeAll
    static void startSystem() throws Exception {
        system = new AuthenticationFlowTest.AuthenticationSystem();
        system.start();
    }

    @AfterAll
    static void stopSystem() throws Exception {
        if (system != null) {
            system.close();
        }
    }

    @Test
    void runningAuthenticationSurvivesNacosOutageAndRecoversStoppedUserInstance() throws Exception {
        var browser = system.newBrowser();
        assertThat(system.login(browser, system.csrf(browser), system.administrator).response().statusCode())
                .isEqualTo(200);
        assertThat(system.get(browser, "/api/v1/users/me").statusCode()).isEqualTo(200);
        system.awaitUserConfigSnapshot();

        system.disconnectNacos();
        try {
            // Both a new login and a protected call use the already discovered target.
            var duringOutage = system.newBrowser();
            assertThat(system.login(duringOutage, system.csrf(duringOutage), system.administrator)
                    .response().statusCode()).isEqualTo(200);
            assertThat(system.get(duringOutage, "/api/v1/users/me").statusCode()).isEqualTo(200);

            var cached = system.startCachedConfigOnlyUser();
            assertThat(cached.healthStatus()).isEqualTo(200);
            assertThat(cached.output()).contains("ingress=false", "configContract=auth-infra-v1");

            var registration = system.startCachedUserWithDiscovery();
            assertThat(registration.exitCode()).isNotZero();
            assertThat(registration.output()).contains("ingress=false")
                    .containsIgnoringCase("client not connected")
                    .doesNotContain("Started UserApplication");
        } finally {
            system.reconnectNacos();
        }

        system.stopUserAndAwaitDeregistration();
        try {
            var unavailable = system.getNoWait(browser, "/api/v1/users/me");
            assertThat(unavailable.statusCode()).as("no healthy user target must fail closed")
                    .isBetween(500, 599);
        } finally {
            system.restartUser();
        }
        system.awaitGatewayIdentity(browser);
        assertThat(system.get(browser, "/api/v1/users/me").statusCode()).isEqualTo(200);
    }

    @Test
    void invalidOrRepeatedUpstreamRequestIdsAreReplaced() throws Exception {
        for (String invalid : java.util.List.of("arbitrary client text", "x".repeat(65), "id.with.dots")) {
            var response = system.directGet(system.newBrowser(), "/api/v1/auth/csrf", Map.of("X-Request-ID", invalid));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("X-Request-ID").orElseThrow())
                    .isNotEqualTo(invalid).matches("[A-Za-z0-9_-]{1,64}");
        }
        var response = system.newBrowser().perform(java.net.http.HttpRequest.newBuilder(
                        system.userUri("/api/v1/auth/csrf")).timeout(Duration.ofSeconds(10))
                .header("X-Request-ID", "first-valid-id").header("X-Request-ID", "second-valid-id").GET());
        assertThat(response.headers().firstValue("X-Request-ID").orElseThrow())
                .isNotIn("first-valid-id", "second-valid-id").matches("[A-Za-z0-9_-]{1,64}");
    }

    @Test
    void logsExcludeCredentialsCookiesCsrfAndSensitiveQueryValues() throws Exception {
        var browser = system.newBrowser();
        var csrf = system.csrf(browser);
        assertThat(system.login(browser, csrf, system.administrator).response().statusCode()).isEqualTo(200);
        var currentCsrf = system.csrf(browser);
        String cookie = browser.cookie("BJSESSION");
        String querySecret = "query-secret-" + java.util.UUID.randomUUID();
        var response = system.getWithHeaders(browser, "/api/v1/users/me?token=" + querySecret,
                Map.of("X-CSRF-TOKEN", currentCsrf.token()));
        assertThat(response.statusCode()).isEqualTo(200);
        String requestId = response.headers().firstValue("X-Request-ID").orElseThrow();
        system.awaitUserApplicationLog("completedRequestId=" + requestId);
        system.awaitGatewayApplicationLog("http requestId=" + requestId);
        system.assertLogsExcludeSecrets(java.util.List.of(cookie, csrf.token(), currentCsrf.token(), querySecret));
    }

    @Test
    void republishedServiceConfigurationAllowsFreshStartup() throws Exception {
        system.removeUserConfig();
        try {
            var absent = system.startProbe(Map.of(), Duration.ofSeconds(60));
            assertThat(absent.exitCode()).isNotZero();
            assertThat(absent.output()).contains("byte-judge.web.ingress")
                    .doesNotContain("Started UserApplication");
        } finally {
            system.restoreUserConfig();
        }
        var recovered = system.startRecoveredConfigUser();
        assertThat(recovered.healthStatus()).isEqualTo(200);
        assertThat(recovered.output()).contains("ingress=false", "configContract=auth-infra-v1");
    }

    @Test
    void missingSharedConfigurationStopsStartup() throws Exception {
        system.removeCommonConfig();
        try {
            var result = system.startProbe(Map.of(), Duration.ofSeconds(45));
            assertThat(result.exitCode()).as(result.output()).isNotZero();
            assertThat(result.output()).contains("byte-judge.config.contract")
                    .doesNotContain("Started UserApplication");
        } finally {
            system.restoreCommonConfig();
        }
    }
}
