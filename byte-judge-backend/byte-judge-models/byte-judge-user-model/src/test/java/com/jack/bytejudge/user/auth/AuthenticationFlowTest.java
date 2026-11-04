package com.jack.bytejudge.user.auth;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationFlowTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static AuthenticationSystem system;

    @org.junit.jupiter.api.BeforeAll
    static void startSystem() throws Exception {
        system = new AuthenticationSystem();
        system.start();
    }

    @org.junit.jupiter.api.AfterAll
    static void stopSystem() throws Exception {
        if (system != null) {
            system.close();
        }
    }

    @Test
    void anonymousBrowserObtainsCsrfTokenAndProtectedSessionCookieThroughGateway() throws Exception {
        var browser = system.newBrowser();
        var csrf = system.csrf(browser);
        assertBasicContract(csrf.response, "/api/v1/auth/csrf");

        var body = JSON.readTree(csrf.response.body());
        assertThat(body.path("code").asInt(-1)).isZero();
        assertThat(body.path("data").path("headerName").asText()).isEqualTo("X-CSRF-TOKEN");
        assertThat(body.path("data").path("token").asText()).isNotBlank();
        assertSessionCookie(csrf.response, browser);
    }

    @Test
    void administratorLogsInThroughGatewayAndSessionIdRotates() throws Exception {
        var browser = system.newBrowser();
        var preLogin = system.csrf(browser);
        String anonymousSession = sessionCookieValue(browser);

        var login = system.login(browser, preLogin, system.administrator);

        assertThat(login.response.statusCode()).as("login response: %s", login.response.body()).isEqualTo(200);
        var body = JSON.readTree(login.response.body());
        assertThat(body.path("code").asInt(-1)).isZero();
        assertThat(body.path("data").path("userId").asText()).matches("\\d+");
        assertThat(body.path("data").path("account").asText()).isEqualTo("http_admin");
        assertThat(body.path("data").path("role").asText()).isEqualTo("admin");
        assertBasicContract(login.response, "/api/v1/auth/login");
        assertSessionCookie(login.response, browser);
        assertThat(sessionCookieValue(browser)).isNotEqualTo(anonymousSession)
                .as("login must rotate the session identifier");

        var identity = system.get(browser, "/api/v1/users/me");
        assertThat(identity.statusCode()).as(identity.body()).isEqualTo(200);
        var identityBody = JSON.readTree(identity.body());
        assertThat(identityBody.path("code").asInt(-1)).isZero();
        assertThat(identityBody.path("data").path("account").asText()).isEqualTo("http_admin");
        assertThat(identityBody.path("data").path("role").asText()).isEqualTo("admin");
        assertThat(identityBody.path("data").size()).as("identity must expose only the safe whitelist").isEqualTo(3);
        assertThat(identity.body()).doesNotContain("password");

        var preLoginBrowser = system.browserWithSession(anonymousSession);
        var stale = system.get(preLoginBrowser, "/api/v1/users/me");
        assertThat(stale.statusCode()).as("the pre-login session id must not carry the logged-in identity")
                .isEqualTo(401);
    }

    @Test
    void logoutEndsOnlyTheCurrentSessionAndClearsItsCredentials() throws Exception {
        var first = system.newBrowser();
        assertThat(system.login(first, system.csrf(first), system.administrator).response.statusCode()).isEqualTo(200);
        var second = system.newBrowser();
        assertThat(system.login(second, system.csrf(second), system.administrator).response.statusCode()).isEqualTo(200);

        String revokedSession = sessionCookieValue(first);
        var token = system.csrf(first);
        var logout = system.logout(first, token);

        assertThat(logout.statusCode()).as(logout.body()).isEqualTo(200);
        var body = JSON.readTree(logout.body());
        assertThat(body.path("code").asInt(-1)).isZero();
        assertThat(body.path("data").isNull()).as("logout returns no data").isTrue();
        assertBasicContract(logout, "/api/v1/auth/logout");
        var sessionCookies = logout.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("BJSESSION=")).toList();
        assertThat(sessionCookies).as("logout must expire the session cookie exactly once").hasSize(1);
        assertThat(sessionCookies.getFirst().toLowerCase(Locale.ROOT))
                .contains("max-age=0", "path=/", "httponly");
        assertThat(first.hasCookie("BJSESSION")).isFalse();

        var staleIdentity = system.get(system.browserWithSession(revokedSession), "/api/v1/users/me");
        assertThat(staleIdentity.statusCode()).as("the revoked cookie must not reach the identity").isEqualTo(401);

        var survivingIdentity = system.get(second, "/api/v1/users/me");
        assertThat(survivingIdentity.statusCode()).as("logout revokes only the current session").isEqualTo(200);
        assertThat(JSON.readTree(survivingIdentity.body()).path("data").path("account").asText())
                .isEqualTo(system.administrator.account());

        var replayedToken = system.logout(system.browserWithSession(revokedSession), token);
        assertThat(replayedToken.statusCode()).as("the pre-logout CSRF token must be cleared").isEqualTo(403);
        assertThat(JSON.readTree(replayedToken.body()).path("code").asInt(-1)).isEqualTo(40301);

        // A fresh token for the now-anonymous browser allows a legitimate login again.
        var relogin = system.login(first, system.csrf(first), system.administrator);
        assertThat(relogin.response.statusCode()).as(relogin.response.body()).isEqualTo(200);
    }

    @Test
    void ordinaryUserSignsInWithTheRoleStoredInTheDatabase() throws Exception {
        var browser = system.newBrowser();
        var login = system.login(browser, system.csrf(browser), system.ordinaryUser);

        assertThat(login.response.statusCode()).as(login.response.body()).isEqualTo(200);
        var data = JSON.readTree(login.response.body()).path("data");
        assertThat(data.path("account").asText()).isEqualTo(system.ordinaryUser.account());
        assertThat(data.path("role").asText()).as("the role comes from the database").isEqualTo("user");

        var identity = system.get(browser, "/api/v1/users/me");
        assertThat(identity.statusCode()).as(identity.body()).isEqualTo(200);
        assertThat(JSON.readTree(identity.body()).path("data").path("role").asText()).isEqualTo("user");
    }

    @Test
    void disabledAccountIsRejectedOnlyAfterItsPasswordVerifies() throws Exception {
        var browser = system.newBrowser();
        var rejected = system.login(browser, system.csrf(browser), system.disabledUser);

        assertThat(rejected.response.statusCode()).as(rejected.response.body()).isEqualTo(403);
        assertThat(JSON.readTree(rejected.response.body()).path("code").asInt(-1)).isEqualTo(40302);
        assertThat(system.get(browser, "/api/v1/users/me").statusCode()).isEqualTo(401);

        var wrongPasswordBrowser = system.newBrowser();
        var wrongPassword = system.login(wrongPasswordBrowser, system.csrf(wrongPasswordBrowser),
                new AuthenticationSystem.FixtureUser(system.disabledUser.account(), "wrong-" + UUID.randomUUID(),
                        system.disabledUser.role(), system.disabledUser.status()));
        assertThat(wrongPassword.response.statusCode()).as("a disabled account must not reveal its state")
                .isEqualTo(401);
    }

    @Test
    void nonexistentAccountAndWrongPasswordProduceTheSameRejection() throws Exception {
        var wrongPasswordBrowser = system.newBrowser();
        var wrongPassword = system.login(wrongPasswordBrowser, system.csrf(wrongPasswordBrowser),
                new AuthenticationSystem.FixtureUser(system.administrator.account(),
                        "wrong-" + UUID.randomUUID(), "admin", "normal"));
        var unknownBrowser = system.newBrowser();
        var unknown = system.login(unknownBrowser, system.csrf(unknownBrowser),
                new AuthenticationSystem.FixtureUser("no_such_account", system.administrator.password(),
                        "user", "normal"));

        assertThat(wrongPassword.response.statusCode()).isEqualTo(401);
        assertThat(unknown.response.statusCode()).isEqualTo(401);
        var wrongBody = JSON.readTree(wrongPassword.response.body());
        var unknownBody = JSON.readTree(unknown.response.body());
        assertThat(wrongBody.path("code").asInt(-1)).isEqualTo(40101);
        assertThat(unknownBody.path("code").asInt(-1)).isEqualTo(40101);
        assertThat(wrongBody.path("message").asText())
                .as("the rejection must not distinguish the two failure causes")
                .isEqualTo(unknownBody.path("message").asText());
        assertThat(wrongBody.path("data").isNull()).isTrue();
        assertThat(wrongPasswordBrowser.hasCookie("BJSESSION")).isTrue();
        assertThat(system.get(wrongPasswordBrowser, "/api/v1/users/me").statusCode()).isEqualTo(401);
    }

    @Test
    void alreadyAuthenticatedBrowserMustLogOutBeforeSigningInAgain() throws Exception {
        var browser = system.newBrowser();
        assertThat(system.login(browser, system.csrf(browser), system.administrator).response.statusCode())
                .isEqualTo(200);

        var secondLogin = system.login(browser, system.csrf(browser), system.ordinaryUser);

        assertThat(secondLogin.response.statusCode()).as(secondLogin.response.body()).isEqualTo(409);
        assertThat(JSON.readTree(secondLogin.response.body()).path("code").asInt(-1)).isEqualTo(40900);
        assertThat(JSON.readTree(system.get(browser, "/api/v1/users/me").body()).path("data").path("account").asText())
                .isEqualTo(system.administrator.account());
    }

    @Test
    void anonymousOrCsrfUnprotectedCallsAreRejectedWithUnifiedErrors() throws Exception {
        var anonymousIdentity = system.get(system.newBrowser(), "/api/v1/users/me");
        assertThat(anonymousIdentity.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(anonymousIdentity.body()).path("code").asInt(-1)).isEqualTo(40100);
        assertBasicContract(anonymousIdentity, "/api/v1/users/me");

        var browser = system.newBrowser();
        var token = system.csrf(browser);
        var missingCsrf = system.post(browser, "/api/v1/auth/login", "application/json", null, null,
                system.loginBody(system.administrator));
        assertThat(missingCsrf.statusCode()).as(missingCsrf.body()).isEqualTo(403);
        assertThat(JSON.readTree(missingCsrf.body()).path("code").asInt(-1)).isEqualTo(40301);
        assertBasicContract(missingCsrf, "/api/v1/auth/login");

        var wrongCsrf = system.post(browser, "/api/v1/auth/login", "application/json",
                token.headerName(), "invalid-" + UUID.randomUUID(), system.loginBody(system.administrator));
        assertThat(wrongCsrf.statusCode()).as(wrongCsrf.body()).isEqualTo(403);
        assertThat(JSON.readTree(wrongCsrf.body()).path("code").asInt(-1)).isEqualTo(40301);

        var anonymousLogout = system.post(browser, "/api/v1/auth/logout", null,
                token.headerName(), token.token(), "");
        assertThat(anonymousLogout.statusCode()).as(anonymousLogout.body()).isEqualTo(401);
        assertThat(JSON.readTree(anonymousLogout.body()).path("code").asInt(-1)).isEqualTo(40100);
    }

    @Test
    void malformedOrUnsupportedLoginRequestsAreRejectedAtTheProtocolBoundary() throws Exception {
        var browser = system.newBrowser();
        var token = system.csrf(browser);

        assertLoginError(system.post(browser, "/api/v1/auth/login", "application/json",
                token.headerName(), token.token(), "{\"account\":\"http_admin\"}"), 400, 40000, browser);
        assertLoginError(system.post(browser, "/api/v1/auth/login", "application/json",
                token.headerName(), token.token(), system.loginBody(system.administrator, "{\"role\":\"admin\"}")),
                400, 40000, browser);
        assertLoginError(system.post(browser, "/api/v1/auth/login", "application/json",
                token.headerName(), token.token(), "{\"account\":\"http_admin\",\"account\":\"http_admin\","
                        + "\"password\":\"" + system.administrator.password() + "\"}"), 400, 40000, browser);
        assertLoginError(system.post(browser, "/api/v1/auth/login", "application/json",
                token.headerName(), token.token(), "{\"account\":12345,\"password\":\""
                        + system.administrator.password() + "\"}"), 400, 40000, browser);
        assertLoginError(system.post(browser, "/api/v1/auth/login", "application/json",
                token.headerName(), token.token(), system.loginBody(system.administrator) + "{}"),
                400, 40000, browser);
        assertLoginError(system.login(browser, token, new AuthenticationSystem.FixtureUser("ab",
                system.administrator.password(), "user", "normal")).response, 400, 40000, browser);
        assertLoginError(system.login(browser, token, new AuthenticationSystem.FixtureUser("http_admin",
                "short", "admin", "normal")).response, 400, 40000, browser);
        assertLoginError(system.post(browser, "/api/v1/auth/login", "text/plain",
                token.headerName(), token.token(), system.loginBody(system.administrator)),
                415, 41500, browser);
        assertLoginError(system.get(browser, "/api/v1/auth/login"), 405, 40500, browser);
        assertLoginError(system.get(browser, "/api/v1/auth/unknown"), 404, 40400, browser);
    }

    @Test
    void requestLogCarriesTheCorrelationIdAndNoCredentials() throws Exception {
        var browser = system.newBrowser();
        var login = system.login(browser, system.csrf(browser), system.administrator);
        assertThat(login.response.statusCode()).isEqualTo(200);

        String requestId = login.response.headers().firstValue("X-Request-ID").orElseThrow();
        assertThat(JSON.readTree(login.response.body()).path("requestId").asText())
                .as("the body and header share one correlation id")
                .isEqualTo(requestId);
        assertThat(requestId).matches("[A-Za-z0-9_-]{1,64}");

        system.awaitUserLog("requestId=" + requestId);
        var userLog = system.userLog();
        assertThat(userLog).contains("method=POST");
        assertThat(userLog).as("request logs never contain credentials").doesNotContain(
                system.administrator.password(), system.ordinaryUser.password());
    }

    private void assertLoginError(HttpResponse<String> response, int status, int code,
                                  AuthenticationSystem.Browser browser) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        var body = JSON.readTree(response.body());
        assertThat(body.path("code").asInt(-1)).isEqualTo(code);
        assertThat(body.path("data").isNull()).isTrue();
        assertThat(browser.hasCookie("BJSESSION")).isTrue();
    }

    private void assertBasicContract(HttpResponse<String> response, String path) {
        assertThat(response.headers().allValues("Cache-Control").stream()
                .flatMap(value -> Arrays.stream(value.toLowerCase(Locale.ROOT).split(",")))
                .map(String::strip).toList()).contains("no-store");
        assertThat(response.headers().firstValue("X-Request-ID")).as(path).isPresent();
    }

    private void assertSessionCookie(HttpResponse<String> response,
                                     AuthenticationSystem.Browser browser) {
        var sessionCookies = response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("BJSESSION=")).toList();
        assertThat(sessionCookies).hasSize(1);
        String sessionCookie = sessionCookies.getFirst();
        assertThat(sessionCookie.substring("BJSESSION=".length()).split(";", 2)[0]).isNotBlank();
        var attributes = Arrays.stream(sessionCookie.toLowerCase(Locale.ROOT).split(";"))
                .map(String::strip).toList();
        assertThat(attributes).contains("httponly", "path=/", "samesite=lax").doesNotContain("secure");
        assertThat(browser.cookie("BJSESSION")).isNotBlank();
    }

    private String sessionCookieValue(AuthenticationSystem.Browser browser) {
        return browser.cookie("BJSESSION");
    }

    /** Actual service processes and disposable infrastructure, reusable by later HTTP flow slices. */
    private static final class AuthenticationSystem implements AutoCloseable {
        private final String runId = UUID.randomUUID().toString().replace("-", "");
        private final java.util.concurrent.atomic.AtomicInteger probeCounter = new java.util.concurrent.atomic.AtomicInteger();
        private final String discoveryGroup = "AUTH_HTTP_" + runId;
        final Administrator administrator = new Administrator("http_admin", "admin-secret-" + UUID.randomUUID());
        final FixtureUser ordinaryUser = new FixtureUser("fixture_user",
                "ordinary-secret-" + UUID.randomUUID(), "user", "normal");
        final FixtureUser disabledUser = new FixtureUser("fixture_disabled",
                "disabled-secret-" + UUID.randomUUID(), "user", "disabled");
        private final Path backend = backendDirectory();
        private final Path logs = backend.resolve("log/build/auth-http-" + runId);
        private final List<AutoCloseable> resources = new ArrayList<>();
        private final List<RunningApplication> applications = new ArrayList<>();
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        private final Map<String, String> environment = new HashMap<>();
        private int gatewayPort;
        private String userClasspath;
        private String gatewayClasspath;
        private MySQLContainer database;

        void start() throws Exception {
            Files.createDirectories(logs);
            Path gatewayTarget = backend.resolve("byte-judge-gateway/target");
            Path classpathFile = gatewayTarget.resolve("test-runtime-classpath.txt");
            assertThat(classpathFile).as("First build Gateway with -am test-compile -DskipTests").isRegularFile();
            assertThat(gatewayTarget.resolve("classes")).isDirectory();
            gatewayClasspath = gatewayTarget.resolve("classes") + System.getProperty("path.separator")
                    + Files.readString(classpathFile, StandardCharsets.UTF_8).strip();
            userClasspath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));

            database = track(new MySQLContainer(DockerImageName.parse("mysql:8.4.13"))
                    .withDatabaseName("auth_http_test").withUsername("auth_http_test")
                    .withPassword(UUID.randomUUID().toString()));
            database.start();
            String redisPassword = UUID.randomUUID().toString();
            var redis = track(new GenericContainer<>(DockerImageName.parse("redis:7.4.12"))
                    .withExposedPorts(6379).withCommand("redis-server", "--requirepass", redisPassword)
                    .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)));
            redis.start();
            var nacos = startNacos();

            environment.put("SPRING_PROFILES_ACTIVE", "auth-http-test");
            environment.put("SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR", nacos.getHost() + ":" + nacos.getMappedPort(8848));
            environment.put("SPRING_CLOUD_NACOS_DISCOVERY_GROUP", discoveryGroup);
            environment.put("SPRING_CLOUD_NACOS_DISCOVERY_IP", "127.0.0.1");
            environment.put("SPRING_CLOUD_NACOS_DISCOVERY_ENABLED", "true");
            environment.put("SPRING_CLOUD_DISCOVERY_ENABLED", "true");
            environment.put("SPRING_DATA_REDIS_HOST", redis.getHost());
            environment.put("SPRING_DATA_REDIS_PORT", redis.getMappedPort(6379).toString());
            environment.put("SPRING_DATA_REDIS_PASSWORD", redisPassword);
            environment.put("SPRING_DATA_REDIS_CONNECT_TIMEOUT", "5s");
            environment.put("SPRING_DATA_REDIS_TIMEOUT", "10s");
            environment.put("SPRING_SESSION_DATA_REDIS_NAMESPACE", "auth-http:" + runId);
            environment.put("BYTE_JUDGE_SESSION_COOKIE_SECURE", "false");
            environment.put("SERVER_ADDRESS", "127.0.0.1");
            environment.put("MANAGEMENT_SERVER_ADDRESS", "127.0.0.1");
            environment.put("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "health");
            environment.put("MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS", "always");
            environment.put("MANAGEMENT_HEALTH_DB_ENABLED", "true");
            environment.put("MANAGEMENT_HEALTH_REDIS_ENABLED", "true");
            environment.put("MANAGEMENT_HEALTH_RABBIT_ENABLED", "false");
            environment.put("BYTE_JUDGE_LOG_DIR", logs.toString());
            environment.put("BYTE_JUDGE_RUNTIME_DIR", logs.resolve("runtime").toString());
            environment.put("SERVER_TOMCAT_ACCESSLOG_ENABLED", "true");
            environment.put("SERVER_TOMCAT_ACCESSLOG_BUFFERED", "false");
            environment.put("SERVER_TOMCAT_ACCESSLOG_PATTERN", "%m %U%q %s");

            var userEnvironment = Map.of(
                    "SPRING_DATASOURCE_URL", database.getJdbcUrl(),
                    "SPRING_DATASOURCE_USERNAME", database.getUsername(),
                    "SPRING_DATASOURCE_PASSWORD", database.getPassword(),
                    "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE", "3",
                    "SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT", "5000",
                    "SPRING_FLYWAY_ENABLED", "true");
            initializeAdministrator(userEnvironment);
            createFixtureUser(ordinaryUser);
            createFixtureUser(disabledUser);

            try (var port = reservePort(); var managementPort = reservePort()) {
                int userPort = port.getLocalPort();
                int healthPort = managementPort.getLocalPort();
                port.close();
                managementPort.close();
                var user = launch("user", userClasspath, "com.jack.bytejudge.user.UserApplication", List.of(),
                        webEnvironment(userEnvironment, "user", userPort, healthPort));
                awaitHealth(user, healthPort, true);
                awaitDiscovery(nacos, "byte-judge-user-model", userPort);
            }
            try (var port = reservePort(); var managementPort = reservePort()) {
                gatewayPort = port.getLocalPort();
                int healthPort = managementPort.getLocalPort();
                port.close();
                managementPort.close();
                var gateway = launch("gateway", gatewayClasspath, "com.jack.bytejudge.gateway.GatewayApplication", List.of(),
                        webEnvironment(Map.of(), "gateway", gatewayPort, healthPort));
                awaitHealth(gateway, healthPort, false);
                awaitDiscovery(nacos, "byte-judge-gateway", gatewayPort);
            }
            System.out.println("AUTH_HTTP_READY profile=auth-http-test group=" + discoveryGroup + " logs=" + logs);
        }

        private GenericContainer<?> startNacos() throws Exception {
            // Nacos derives the client gRPC port from the API port + 1000. Reserve BOTH
            // host ports before asking Docker to bind them; arbitrary independent mappings break discovery.
            int apiPort;
            try (var pair = reserveNacosPorts()) {
                apiPort = pair.api().getLocalPort();
            }
            String token = Base64.getEncoder().encodeToString((UUID.randomUUID().toString()
                    + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
            var nacos = track(new GenericContainer<>(DockerImageName.parse("nacos/nacos-server:v3.3.0"))
                    .withExposedPorts(8848, 9848, 8080)
                    .withEnv("MODE", "standalone").withEnv("NACOS_AUTH_ENABLE", "false")
                    .withEnv("NACOS_AUTH_TOKEN", token)
                    .withEnv("NACOS_AUTH_IDENTITY_KEY", "auth-http-test")
                    .withEnv("NACOS_AUTH_IDENTITY_VALUE", runId)
                    .withEnv("JVM_XMS", "256m").withEnv("JVM_XMX", "512m").withEnv("JVM_XMN", "128m")
                    .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
                            binding(apiPort, 8848), binding(apiPort + 1000, 9848), binding(0, 8080)))
                    .waitingFor(Wait.forHttp("/v3/console/health/readiness").forPort(8080)
                            .forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3))));
            nacos.start();
            assertThat(nacos.getMappedPort(9848)).isEqualTo(nacos.getMappedPort(8848) + 1000);
            return nacos;
        }

        private void initializeAdministrator(Map<String, String> userEnvironment) throws Exception {
            var admin = launch("create-admin", userClasspath, "com.jack.bytejudge.user.UserApplication",
                    List.of("--create-admin"), userEnvironment);
            byte[] input = JSON.writeValueAsBytes(Map.of("account", administrator.account,
                    "password", administrator.password, "confirmation", administrator.password));
            try (var stdin = admin.process().getOutputStream()) {
                stdin.write(input);
            }
            assertThat(admin.process().waitFor(45, TimeUnit.SECONDS)).as("Admin command timeout; %s", admin.log()).isTrue();
            assertThat(admin.process().exitValue()).as(admin.output()).isZero();
            assertThat(admin.output()).contains("ADMIN_CREATED").doesNotContain(administrator.password);
        }

        /** Ordinary and disabled accounts exist only as fixtures; the slice has no public registration. */
        private void createFixtureUser(FixtureUser user) throws Exception {
            PasswordEncoder encoder = new DelegatingPasswordEncoder("argon2",
                    Map.of("argon2", new Argon2PasswordEncoder(16, 32, 1, 65_536, 3)));
            try (var connection = java.sql.DriverManager.getConnection(
                    database.getJdbcUrl(), database.getUsername(), database.getPassword());
                 var statement = connection.prepareStatement(
                         "INSERT INTO user_account (account, password_hash, role, status) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, user.account());
                statement.setString(2, encoder.encode(user.password()));
                statement.setString(3, user.role());
                statement.setString(4, user.status());
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
        }

        private Map<String, String> webEnvironment(Map<String, String> extra, String service, int port, int healthPort) {
            var result = new HashMap<>(extra);
            result.put("SERVER_PORT", Integer.toString(port));
            result.put("MANAGEMENT_SERVER_PORT", Integer.toString(healthPort));
            result.put("SERVER_TOMCAT_BASEDIR", logs.resolve("runtime/" + service).toString());
            result.put("SERVER_TOMCAT_ACCESSLOG_DIRECTORY", logs.resolve(service + "/access").toString());
            return result;
        }

        private RunningApplication launch(String name, String classpath, String mainClass,
                                          List<String> options, Map<String, String> overrides) throws Exception {
            Path arguments = logs.resolve(name + ".args");
            Files.writeString(arguments, "-Xmx256m\n-Dfile.encoding=UTF-8\n-DJM.LOG.PATH=\""
                    + logs.resolve(name + "/nacos").toString().replace('\\', '/')
                    + "\"\n-cp\n\"" + classpath.replace('\\', '/') + "\"\n" + mainClass + "\n"
                    + String.join("\n", options) + "\n", StandardCharsets.UTF_8);
            Path output = logs.resolve(name + ".console.log");
            var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "@" + arguments).directory(logs.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
            // Keep only OS essentials. Inherited Spring/infra/JVM settings must never select a local environment.
            var inherited = new HashMap<>(builder.environment());
            builder.environment().clear();
            for (String key : List.of("SystemRoot", "WINDIR", "ComSpec", "PATH", "PATHEXT", "TEMP", "TMP",
                    "USERPROFILE", "HOME", "APPDATA", "LOCALAPPDATA")) {
                if (inherited.containsKey(key)) {
                    builder.environment().put(key, inherited.get(key));
                }
            }
            builder.environment().putAll(environment);
            builder.environment().putAll(overrides);
            var application = new RunningApplication(builder.start(), output);
            applications.add(application);
            resources.add(application);
            return application;
        }

        Browser newBrowser() {
            return new Browser();
        }

        Browser browserWithSession(String sessionId) {
            var browser = new Browser();
            browser.cookies.put("BJSESSION", sessionId);
            return browser;
        }

        /** Unique per request so downstream access evidence cannot be satisfied by an earlier request. */
        String probe(String path) {
            return path + (path.contains("?") ? "&" : "?") + "harnessProbe=" + runId + "-" + probeCounter.incrementAndGet();
        }

        /**
         * Explicit cookie jar: the JDK CookieManager silently drops this service's rotated session
         * cookie, and the jar also lets the tests state cookie clearing and rotation precisely.
         */
        final class Browser {
            private final Map<String, String> cookies = new java.util.LinkedHashMap<>();
            private final HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3)).build();

            String cookie(String name) {
                String value = cookies.get(name);
                if (value == null) {
                    throw new AssertionError("missing " + name + " cookie");
                }
                return value;
            }

            boolean hasCookie(String name) {
                return cookies.containsKey(name);
            }

            HttpResponse<String> perform(HttpRequest.Builder builder) throws Exception {
                if (!cookies.isEmpty()) {
                    builder.header("Cookie", cookies.entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(java.util.stream.Collectors.joining("; ")));
                }
                var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                for (String setCookie : response.headers().allValues("Set-Cookie")) {
                    String[] parts = setCookie.split(";");
                    String[] pair = parts[0].split("=", 2);
                    String name = pair[0].strip();
                    String value = pair.length > 1 ? pair[1].strip() : "";
                    boolean cleared = value.isEmpty() || Arrays.stream(parts)
                            .anyMatch(part -> part.strip().toLowerCase(Locale.ROOT).startsWith("max-age=0"));
                    if (cleared) {
                        cookies.remove(name);
                    } else {
                        cookies.put(name, value);
                    }
                }
                return response;
            }
        }

        record CsrfToken(HttpResponse<String> response, String headerName, String token) { }

        CsrfToken csrf(Browser browser) throws Exception {
            String path = probe("/api/v1/auth/csrf");
            var response = browser.perform(HttpRequest.newBuilder(gatewayUri(path))
                    .timeout(Duration.ofSeconds(10)).GET());
            awaitUserAccess("GET", path, response.statusCode());
            var body = JSON.readTree(response.body());
            return new CsrfToken(response, body.path("data").path("headerName").asText(),
                    body.path("data").path("token").asText());
        }

        record LoginResult(HttpResponse<String> response) { }

        LoginResult login(Browser browser, CsrfToken token, Credentials credentials) throws Exception {
            return login(browser, token, credentials, "");
        }

        LoginResult login(Browser browser, CsrfToken token, Credentials credentials, String extraJson)
                throws Exception {
            return new LoginResult(post(browser, "/api/v1/auth/login", "application/json",
                    token.headerName(), token.token(), loginBody(credentials, extraJson)));
        }

        private String loginBody(Credentials credentials) throws Exception {
            return loginBody(credentials, "");
        }

        private String loginBody(Credentials credentials, String extraJson) throws Exception {
            var fields = new java.util.LinkedHashMap<>(Map.of("account", credentials.account(),
                    "password", credentials.password()));
            if (!extraJson.isEmpty()) {
                fields.putAll(JSON.readValue(extraJson, Map.class));
            }
            return JSON.writeValueAsString(fields);
        }

        HttpResponse<String> logout(Browser browser, CsrfToken token) throws Exception {
            return post(browser, "/api/v1/auth/logout", null, token.headerName(), token.token(), "");
        }

        HttpResponse<String> post(Browser browser, String path, String contentType,
                                  String headerName, String token, String body) throws Exception {
            String probed = probe(path);
            var builder = HttpRequest.newBuilder(gatewayUri(probed)).timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (contentType != null) {
                builder.header("Content-Type", contentType);
            }
            if (token != null) {
                builder.header(headerName, token);
            }
            var response = browser.perform(builder);
            awaitUserAccess("POST", probed, response.statusCode());
            return response;
        }

        Path userConsoleLog() {
            return logs.resolve("user.console.log");
        }

        String userLog() throws IOException {
            return Files.readString(userConsoleLog(), StandardCharsets.UTF_8);
        }

        void awaitUserLog(String expected) throws Exception {
            await("user log containing " + expected, Duration.ofSeconds(20),
                    () -> Files.isRegularFile(userConsoleLog()) && userLog().contains(expected));
        }

        HttpResponse<String> get(Browser browser, String path) throws Exception {
            String probed = probe(path);
            var response = browser.perform(HttpRequest.newBuilder(gatewayUri(probed))
                    .timeout(Duration.ofSeconds(10)).GET());
            awaitUserAccess("GET", probed, response.statusCode());
            return response;
        }

        interface Credentials {
            String account();

            String password();
        }

        record Administrator(String account, String password) implements Credentials { }

        record FixtureUser(String account, String password, String role, String status) implements Credentials { }

        URI gatewayUri(String path) {
            return URI.create("http://127.0.0.1:" + gatewayPort + path);
        }

        private void awaitHealth(RunningApplication application, int port, boolean databaseAndRedis) throws Exception {
            await("management health on " + port, Duration.ofSeconds(90), () -> {
                var response = get(URI.create("http://127.0.0.1:" + port + "/actuator/health"));
                var health = JSON.readTree(response.body());
                if (response.statusCode() != 200 || !health.path("status").asText().equals("UP")) {
                    return false;
                }
                if (databaseAndRedis) {
                    assertThat(health.path("components").path("db").path("status").asText()).isEqualTo("UP");
                    assertThat(health.path("components").path("redis").path("status").asText()).isEqualTo("UP");
                }
                System.out.println("AUTH_HTTP_HEALTH " + application.log().getFileName() + " " + response.body());
                return true;
            });
        }

        private void awaitDiscovery(GenericContainer<?> nacos, String service, int port) throws Exception {
            URI uri = URI.create("http://" + nacos.getHost() + ":" + nacos.getMappedPort(8848)
                    + "/nacos/v3/client/ns/instance/list?serviceName=" + service + "&groupName=" + discoveryGroup);
            await("healthy Nacos registration for " + service, Duration.ofSeconds(45), () -> {
                var response = get(uri);
                var body = JSON.readTree(response.body());
                if (response.statusCode() == 200 && body.path("code").asInt(-1) == 0) {
                    for (var instance : body.path("data")) {
                        if (instance.path("healthy").asBoolean() && instance.path("port").asInt() == port
                                && instance.path("ip").asText().equals("127.0.0.1")) {
                            System.out.println("AUTH_HTTP_DISCOVERY " + service + " port=" + port);
                            return true;
                        }
                    }
                }
                return false;
            });
        }

        void awaitUserAccess(String method, String path, int status) throws Exception {
            Path directory = logs.resolve("user/access");
            await("downstream user access for " + path, Duration.ofSeconds(20), () -> {
                if (!Files.isDirectory(directory)) {
                    return false;
                }
                try (var files = Files.list(directory)) {
                    for (var file : files.filter(Files::isRegularFile).toList()) {
                        for (var line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                            // Access pattern is "%m %U%q %s"; %q renders "-" when the URL has no query.
                            if (line.contains(method + " " + path) && line.endsWith(" " + status)) {
                                System.out.println("AUTH_HTTP_DOWNSTREAM " + method + " " + path + " " + status);
                                return true;
                            }
                        }
                    }
                }
                return false;
            });
        }

        private HttpResponse<String> get(URI uri) throws IOException, InterruptedException {
            return http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        private void await(String description, Duration timeout, CheckedCondition condition) throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            IOException lastConnectionFailure = null;
            while (System.nanoTime() < deadline) {
                for (var application : applications) {
                    if (!application.log().getFileName().toString().startsWith("create-admin")) {
                        assertThat(application.process().isAlive()).as(application.output()).isTrue();
                    }
                }
                try {
                    if (condition.test()) {
                        return;
                    }
                } catch (IOException unavailable) {
                    lastConnectionFailure = unavailable;
                }
                TimeUnit.MILLISECONDS.sleep(200);
            }
            throw new AssertionError("Timed out waiting for " + description + "; logs: " + logs, lastConnectionFailure);
        }

        private <T extends AutoCloseable> T track(T resource) {
            resources.add(resource);
            return resource;
        }

        @Override
        public void close() throws Exception {
            Exception failure = null;
            for (var resource : resources.reversed()) {
                try {
                    resource.close();
                } catch (Exception cleanupFailure) {
                    if (failure == null) {
                        failure = cleanupFailure;
                    } else {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static Path backendDirectory() {
        Path directory = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("byte-judge-gateway/pom.xml"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Cannot locate backend root from the test working directory");
    }

    private static ServerSocket reservePort() throws IOException {
        return new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
    }

    private static NacosPorts reserveNacosPorts() throws IOException {
        // Windows excludes several high ranges; choose a random low-range pair and prove BOTH ports
        // bind on loopback before Docker binds them.
        for (int attempt = 0; attempt < 400; attempt++) {
            int apiPort = java.util.concurrent.ThreadLocalRandom.current().nextInt(20_000, 45_000);
            ServerSocket api;
            try {
                api = new ServerSocket(apiPort, 1, InetAddress.getByName("127.0.0.1"));
            } catch (IOException occupiedApiPort) {
                continue;
            }
            try {
                var grpc = new ServerSocket(apiPort + 1000, 1, InetAddress.getByName("127.0.0.1"));
                return new NacosPorts(api, grpc);
            } catch (IOException occupiedGrpcPort) {
                api.close();
            }
        }
        throw new IOException("Cannot reserve an available Nacos API/gRPC host port pair");
    }

    private static PortBinding binding(int host, int container) {
        return new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", host), new ExposedPort(container));
    }

    private record NacosPorts(ServerSocket api, ServerSocket grpc) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            try (api; grpc) {
                // Release both reservations immediately before Docker binds the pair.
            }
        }
    }

    private record RunningApplication(Process process, Path log) implements AutoCloseable {
        String output() throws IOException {
            return Files.readString(log, StandardCharsets.UTF_8);
        }

        @Override
        public void close() throws InterruptedException {
            if (process.isAlive()) {
                process.destroy();
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    if (!process.waitFor(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Application did not exit: " + log);
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface CheckedCondition {
        boolean test() throws Exception;
    }
}
