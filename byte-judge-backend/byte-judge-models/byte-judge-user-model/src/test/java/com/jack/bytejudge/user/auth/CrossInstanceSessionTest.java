package com.jack.bytejudge.user.auth;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #9 acceptance through the real gateway and two live user-service processes that share one
 * artifact, database, Redis and session namespace. Every assertion is made over external HTTP or
 * on the durable revocation fact; the only coordination hook is a test-classpath filter that holds
 * a request after it has been authorized and before its response is saved.
 */
class CrossInstanceSessionTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String RACE_HEADER = "X-Auth9-Race";
    private static final String RACE_TOKEN_HEADER = "X-Auth9-Race-Token";
    private static AuthenticationFlowTest.AuthenticationSystem system;

    @BeforeAll
    static void startSystem() throws Exception {
        system = new AuthenticationFlowTest.AuthenticationSystem(Map.of(), 2);
        system.start();
    }

    @AfterAll
    static void stopSystem() throws Exception {
        if (system != null) {
            system.close();
        }
    }

    @Test
    void oneCookieReachesBothUserInstancesWithTheSameIdentity() throws Exception {
        var browser = login(system.administrator);
        String cookie = browser.cookie("BJSESSION");
        Set<String> instances = new LinkedHashSet<>();

        for (int attempt = 0; attempt < 30 && instances.size() < 2; attempt++) {
            // Protected requests consume an extra load-balancer pick for the gateway's identity
            // verification. Alternating a public request keeps the proxy picks from being pinned
            // to one parity, so this loop can actually observe both processes serving the session.
            system.csrf(browser);
            var response = system.get(browser, "/api/v1/users/me");
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            var data = JSON.readTree(response.body()).path("data");
            assertThat(data.path("account").asText()).isEqualTo(system.administrator.account());
            assertThat(data.path("role").asText()).isEqualTo("admin");
            assertThat(data.size()).as("identity must stay the safe three-field contract").isEqualTo(3);
            instances.add(system.awaitUserInstance("GET", "/api/v1/users/me", response));
            assertThat(browser.cookie("BJSESSION"))
                    .as("the same cookie must remain valid across instances").isEqualTo(cookie);
        }

        assertThat(instances).as("the same session must actually be served by both processes")
                .containsExactlyInAnyOrder("user", "user-b");
    }

    @Test
    void productionSessionDefaultsAreThirtyMinutesAndTheSharedCookieContract() throws Exception {
        var browser = system.newBrowser();
        var csrf = system.csrf(browser);
        var login = system.login(browser, csrf, system.administrator);
        assertThat(login.response().statusCode()).as(login.response().body()).isEqualTo(200);

        String cookie = browser.cookie("BJSESSION");
        assertThat(cookie).doesNotContain("=", " ");
        assertThat(system.sessionMaxInactiveInterval(cookie))
                .as("the running instance must apply the production 30-minute default")
                .isEqualTo(1800);
        assertThat(system.sessionRedisKey(cookie))
                .as("both services must share the configured namespace")
                .startsWith("auth-http:").contains(":sessions:");

        var sessionCookies = login.response().headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("BJSESSION=")).toList();
        assertThat(sessionCookies).hasSize(1);
        var attributes = Arrays.stream(sessionCookies.getFirst().toLowerCase(Locale.ROOT).split(";"))
                .map(String::strip).toList();
        assertThat(attributes).contains("path=/", "httponly", "samesite=lax")
                .as("local profile only disables Secure").doesNotContain("secure");
    }

    @Test
    void logoutRevokesOnlyTheCurrentSessionEverywhereAndPersistsTheFact() throws Exception {
        var first = login(system.administrator);
        var second = login(system.administrator);
        String revoked = first.cookie("BJSESSION");

        var logout = system.logout(first, system.csrf(first));
        assertThat(logout.statusCode()).as(logout.body()).isEqualTo(200);
        assertThat(first.hasCookie("BJSESSION")).as("logout clears the browser cookie").isFalse();

        assertThat(system.revokedSessionCount(revoked))
                .as("logout commits a durable tombstone before acknowledging success").isEqualTo(1);
        assertThat(system.revokedSessionHashLength(revoked))
                .as("only a SHA-256 digest, never the session id, is stored").isEqualTo(32);

        assertThat(system.getNoWait(browser(revoked), "/api/v1/users/me").statusCode())
                .as("the gateway must reject the revoked cookie").isEqualTo(401);
        assertThat(system.directGetOn(system.primaryUserPort(), browser(revoked),
                "/api/v1/users/me", Map.of()).statusCode())
                .as("a direct call to the first instance must not bypass revocation").isEqualTo(401);
        assertThat(system.directGetOn(system.secondUserPort(), browser(revoked),
                "/api/v1/users/me", Map.of()).statusCode())
                .as("a direct call to the second instance must not bypass revocation").isEqualTo(401);

        assertThat(system.get(second, "/api/v1/users/me").statusCode())
                .as("an independent session must survive another session's logout").isEqualTo(200);
        assertThat(system.directGetOn(system.primaryUserPort(), second,
                "/api/v1/users/me", Map.of()).statusCode()).isEqualTo(200);
        assertThat(system.directGetOn(system.secondUserPort(), second,
                "/api/v1/users/me", Map.of()).statusCode()).isEqualTo(200);
    }

    @Test
    void aRequestThatSavesAfterLogoutCannotRestoreTheOldCookie() throws Exception {
        var browser = login(system.administrator);
        String oldCookie = browser.cookie("BJSESSION");
        String token = "race-" + UUID.randomUUID();
        var future = holdRequestOnFirstInstance(oldCookie, token);
        system.awaitRaceLoaded(token);
        assertThat(system.raceLoadedSessionId(token)).isEqualTo(oldCookie);

        // Revoke from the other instance while the first request is still holding its response.
        var logoutBrowser = browser(oldCookie);
        var csrf = system.csrfDirectOn(system.secondUserPort(), logoutBrowser);
        var logout = system.directPostOn(system.secondUserPort(), logoutBrowser,
                "/api/v1/auth/logout", Map.of(csrf.headerName(), csrf.token()), "");
        assertThat(logout.statusCode()).as(logout.body()).isEqualTo(200);

        system.releaseRace(token);
        var held = future.get(60, TimeUnit.SECONDS);
        assertThat(held.statusCode())
                .as("the request was authorized before the revocation and may finish").isEqualTo(200);
        // Its response is saved by Spring Session during commit; prove that write actually happened
        // so the rejection below cannot be explained by "nothing was written".
        awaitSessionHashRecreated(oldCookie);

        assertThat(system.getNoWait(browser(oldCookie), "/api/v1/users/me").statusCode())
                .as("the gateway must still reject the replayed cookie").isEqualTo(401);
        assertThat(system.directGetOn(system.primaryUserPort(), browser(oldCookie),
                "/api/v1/users/me", Map.of()).statusCode())
                .as("the instance that saw the stale save must reject it").isEqualTo(401);
        assertThat(system.directGetOn(system.secondUserPort(), browser(oldCookie),
                "/api/v1/users/me", Map.of()).statusCode()).isEqualTo(401);
        assertThat(system.revokedSessionCount(oldCookie)).isEqualTo(1);
    }

    @Test
    void aCompleteStaleSessionStillCannotAuthenticateAfterLogout() throws Exception {
        var browser = login(system.administrator);
        String cookie = browser.cookie("BJSESSION");
        Map<Object, Object> completeHash = system.snapshotSession(cookie);
        assertThat(completeHash).as("the snapshot must be a complete session, not a fragment")
                .containsKeys("creationTime", "lastAccessedTime", "maxInactiveInterval");

        assertThat(system.logout(browser, system.csrf(browser)).statusCode()).isEqualTo(200);

        // Reproduce the state the issue forbids relying on deletion for: Redis holds the full old
        // session again, exactly as a stale node or an interrupted delete could leave it.
        system.restoreSession(cookie, completeHash);
        assertThat(system.sessionMaxInactiveInterval(cookie))
                .as("the hash really is complete and readable again").isEqualTo(1800);

        assertThat(system.getNoWait(browser(cookie), "/api/v1/users/me").statusCode())
                .as("the gateway must consult the durable fact, not Redis contents").isEqualTo(401);
        assertThat(system.directGetOn(system.primaryUserPort(), browser(cookie),
                "/api/v1/users/me", Map.of()).statusCode()).isEqualTo(401);
        assertThat(system.directGetOn(system.secondUserPort(), browser(cookie),
                "/api/v1/users/me", Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void anAttributeOnlyFragmentLeftByAStaleSaveIsRejectedAsUnauthenticated() throws Exception {
        var browser = login(system.administrator);
        String cookie = browser.cookie("BJSESSION");

        // Expiry (or an interrupted delete) removed the hash while a request still held the
        // session; its delta save recreates only the attribute entry. No tombstone exists here, so
        // this exercises the incomplete-hash path on its own.
        system.replaceSessionWithFragment(cookie, "auth9Fragment");
        var fragment = system.snapshotSession(cookie);
        assertThat(system.sessionHashExists(cookie)).isTrue();
        assertThat(fragment).containsKey("sessionAttr:auth9Fragment")
                .as("the fixture must be a fragment, not a complete session")
                .doesNotContainKeys("creationTime", "lastAccessedTime", "maxInactiveInterval");

        assertThat(system.getNoWait(browser(cookie), "/api/v1/users/me").statusCode())
                .as("an incomplete hash must read as unauthenticated, never as a server error")
                .isEqualTo(401);
        assertThat(system.directGetOn(system.primaryUserPort(), browser(cookie),
                "/api/v1/users/me", Map.of()).statusCode()).isEqualTo(401);
        assertThat(system.directGetOn(system.secondUserPort(), browser(cookie),
                "/api/v1/users/me", Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void disabledAccountRejectsTheNextRequestAndRecoveryDoesNotReviveTheSession() throws Exception {
        var fixture = system.uniqueFixtureUser("auth9status", "user", "normal");
        var browser = login(fixture);
        String oldCookie = browser.cookie("BJSESSION");
        String token = "status-" + UUID.randomUUID();
        var future = holdRequestOnFirstInstance(oldCookie, token);
        system.awaitRaceLoaded(token);

        system.setStatus(fixture.account(), "disabled");
        assertThat(system.directGetOn(system.secondUserPort(), browser(oldCookie),
                "/api/v1/users/me", Map.of()).statusCode())
                .as("the next protected request after disabling must be refused").isEqualTo(401);
        system.setStatus(fixture.account(), "normal");

        system.releaseRace(token);
        assertThat(future.get(60, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        awaitSessionHashRecreated(oldCookie);

        assertThat(system.getNoWait(browser(oldCookie), "/api/v1/users/me").statusCode())
                .as("restoring the account must not revive the rejected session").isEqualTo(401);
        assertThat(system.directGetOn(system.primaryUserPort(), browser(oldCookie),
                "/api/v1/users/me", Map.of()).statusCode()).isEqualTo(401);

        var fresh = login(fixture);
        assertThat(system.get(fresh, "/api/v1/users/me").statusCode())
                .as("only a new login may establish the restored identity").isEqualTo(200);
    }

    @Test
    void changedAuthEpochRejectsTheSessionAndOnlyReLoginWorks() throws Exception {
        var fixture = system.uniqueFixtureUser("auth9epoch", "user", "normal");
        var browser = login(fixture);
        String oldCookie = browser.cookie("BJSESSION");
        long originalEpoch = system.authEpoch(fixture.account());

        system.bumpAuthEpoch(fixture.account());

        assertThat(system.getNoWait(browser(oldCookie), "/api/v1/users/me").statusCode())
                .as("a changed authentication epoch must reject the old identity").isEqualTo(401);
        assertThat(system.directGetOn(system.secondUserPort(), browser(oldCookie),
                "/api/v1/users/me", Map.of()).statusCode()).isEqualTo(401);

        // Put the previous version back, as a rolled-back fixture change would. The rejection was
        // already made durable, so the old session must stay dead.
        system.setAuthEpoch(fixture.account(), originalEpoch);
        assertThat(system.authEpoch(fixture.account())).isEqualTo(originalEpoch);
        assertThat(system.getNoWait(browser(oldCookie), "/api/v1/users/me").statusCode())
                .as("restoring the authentication epoch must not revive the rejected session")
                .isEqualTo(401);
        assertThat(system.directGetOn(system.primaryUserPort(), browser(oldCookie),
                "/api/v1/users/me", Map.of()).statusCode()).isEqualTo(401);

        var fresh = login(fixture);
        assertThat(system.get(fresh, "/api/v1/users/me").statusCode())
                .as("a new login after the epoch change must work").isEqualTo(200);
    }

    @Test
    void revokedSessionStaysRejectedAfterTheUserInstanceRestarts() throws Exception {
        var browser = login(system.administrator);
        String revoked = browser.cookie("BJSESSION");
        assertThat(system.logout(browser, system.csrf(browser)).statusCode()).isEqualTo(200);

        system.restartUser();

        assertThat(system.getNoWait(browser(revoked), "/api/v1/users/me").statusCode())
                .as("the durable tombstone, not process memory, must survive the restart").isEqualTo(401);
        assertThat(system.directGetOn(system.primaryUserPort(), browser(revoked),
                "/api/v1/users/me", Map.of()).statusCode()).isEqualTo(401);
        assertThat(system.directGetOn(system.secondUserPort(), browser(revoked),
                "/api/v1/users/me", Map.of()).statusCode()).isEqualTo(401);
    }

    private static AuthenticationFlowTest.AuthenticationSystem.Browser login(
            AuthenticationFlowTest.AuthenticationSystem.Credentials credentials) throws Exception {
        var browser = system.newBrowser();
        var result = system.login(browser, system.csrf(browser), credentials);
        assertThat(result.response().statusCode()).as(result.response().body()).isEqualTo(200);
        return browser;
    }

    private static AuthenticationFlowTest.AuthenticationSystem.Browser browser(String sessionId) {
        return system.browserWithSession(sessionId);
    }

    private static CompletableFuture<HttpResponse<String>> holdRequestOnFirstInstance(
            String sessionId, String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return system.directGetOn(system.primaryUserPort(), browser(sessionId),
                        "/api/v1/users/me",
                        Map.of(RACE_HEADER, "touch", RACE_TOKEN_HEADER, token));
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        });
    }

    private static void awaitSessionHashRecreated(String sessionId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (system.sessionHashExists(sessionId)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The coordinated request never saved its stale session");
    }
}
