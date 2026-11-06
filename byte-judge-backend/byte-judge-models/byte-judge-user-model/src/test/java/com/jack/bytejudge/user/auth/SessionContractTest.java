package com.jack.bytejudge.user.auth;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idle expiry and activity renewal against real HTTP. The test overrides only the idle duration so
 * the boundary can be observed in seconds; the production default of 1800 seconds is asserted
 * separately against a running instance in {@link CrossInstanceSessionTest}.
 */
class SessionContractTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int IDLE_SECONDS = 30;
    private static AuthenticationFlowTest.AuthenticationSystem system;

    @BeforeAll
    static void startSystem() throws Exception {
        system = new AuthenticationFlowTest.AuthenticationSystem(
                Map.of("SPRING_SESSION_TIMEOUT", IDLE_SECONDS + "s"), 1);
        system.start();
    }

    @AfterAll
    static void stopSystem() throws Exception {
        if (system != null) {
            system.close();
        }
    }

    @Test
    void idleSessionExpiresOnTheGatewayAndOnTheOwningInstance() throws Exception {
        var browser = login();
        String cookie = browser.cookie("BJSESSION");
        assertThat(system.sessionMaxInactiveInterval(cookie))
                .as("the running instance must use the configured test duration")
                .isEqualTo(IDLE_SECONDS);

        TimeUnit.SECONDS.sleep(IDLE_SECONDS + 4);

        assertThat(system.getNoWait(browser, "/api/v1/users/me").statusCode())
                .as("an idle session must not pass the gateway").isEqualTo(401);
        assertThat(system.directGetOn(system.primaryUserPort(), system.browserWithSession(cookie),
                "/api/v1/users/me", Map.of()).statusCode())
                .as("a direct call must not bypass idle expiry").isEqualTo(401);
    }

    @Test
    void activityBeforeTheIdleDeadlineRenewsTheSession() throws Exception {
        var browser = login();

        // First access well before the idle deadline.
        TimeUnit.SECONDS.sleep(IDLE_SECONDS * 2 / 5);
        assertThat(system.get(browser, "/api/v1/users/me").statusCode()).isEqualTo(200);

        // The total time since login is now longer than the idle duration, but the gap since the
        // previous access is still shorter: the session must have been renewed by the activity.
        TimeUnit.SECONDS.sleep(IDLE_SECONDS * 3 / 4);
        var renewed = system.get(browser, "/api/v1/users/me");
        assertThat(renewed.statusCode()).as(renewed.body())
                .as("activity must renew the idle deadline").isEqualTo(200);
        assertThat(JSON.readTree(renewed.body()).path("data").path("account").asText())
                .isEqualTo(system.administrator.account());

        // No activity for longer than the idle duration: expiry still applies afterwards.
        TimeUnit.SECONDS.sleep(IDLE_SECONDS + 4);
        assertThat(system.getNoWait(browser, "/api/v1/users/me").statusCode())
                .as("stopping activity must let the renewed session expire").isEqualTo(401);
    }

    private static AuthenticationFlowTest.AuthenticationSystem.Browser login() throws Exception {
        var browser = system.newBrowser();
        var result = system.login(browser, system.csrf(browser), system.administrator);
        assertThat(result.response().statusCode()).as(result.response().body()).isEqualTo(200);
        return browser;
    }
}
