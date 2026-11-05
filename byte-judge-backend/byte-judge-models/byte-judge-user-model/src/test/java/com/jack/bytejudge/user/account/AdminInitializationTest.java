package com.jack.bytejudge.user.account;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AdminInitializationTest {
    private static final MySQLContainer DATABASE = new MySQLContainer(DockerImageName.parse("mysql:8.4.13"))
            .withDatabaseName("admin_creation_test")
            .withUsername("admin_creation_test")
            .withPassword(UUID.randomUUID().toString());
    private static final String REDIS_PASSWORD = UUID.randomUUID().toString();
    /** Normal web startup wires the shared Redis session; the command path must not need it. */
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4.12"))
            .withExposedPorts(6379).withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));

    @TempDir
    static Path temporaryDirectory;

    @BeforeAll
    static void startDatabase() {
        DATABASE.start();
        REDIS.start();
    }

    @AfterAll
    static void stopDatabase() {
        REDIS.stop();
        DATABASE.stop();
    }

    @Test
    void deploymentCommandCreatesANormalizedAdministratorAndExits() throws Exception {
        var result = initialize("  First_Admin  ", "first-test-password", "first-test-password");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("ADMIN_CREATED", "first_admin")
                .doesNotContain("first-test-password");
        try (var connection = connect(); var statement = connection.prepareStatement(
                "SELECT role, status, auth_epoch FROM user_account WHERE account = ?")) {
            statement.setString(1, "first_admin");
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("role")).isEqualTo("admin");
                assertThat(rows.getString("status")).isEqualTo("normal");
                assertThat(rows.getLong("auth_epoch")).isZero();
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @Test
    void commandRejectsMismatchedConfirmationWithoutExposingEitherPassword() throws Exception {
        var result = initialize("mismatch_admin", "first-secret-value", "other-secret-value");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.output()).contains("ADMIN_INPUT_INVALID")
                .doesNotContain("first-secret-value", "other-secret-value", "ADMIN_CREATED");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("invalidCredentials")
    void commandRejectsInvalidAccountOrPassword(String account, String password) throws Exception {
        var result = initialize(account, password, password);

        assertThat(result.exitCode()).as("Invalid input must have a distinct exit code").isEqualTo(2);
        assertThat(result.output()).contains("ADMIN_INPUT_INVALID").doesNotContain("ADMIN_CREATED", password);
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> invalidCredentials() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("abc", "valid-test-password"),
                org.junit.jupiter.params.provider.Arguments.of("a".repeat(33), "valid-test-password"),
                org.junit.jupiter.params.provider.Arguments.of("bad-name", "valid-test-password"),
                org.junit.jupiter.params.provider.Arguments.of("管理账户", "valid-test-password"),
                org.junit.jupiter.params.provider.Arguments.of("Kabc", "valid-test-password"),
                org.junit.jupiter.params.provider.Arguments.of("short_password", "seven77"),
                org.junit.jupiter.params.provider.Arguments.of("long_password", "p".repeat(65)),
                org.junit.jupiter.params.provider.Arguments.of("short_unicode", "🔐".repeat(7)),
                org.junit.jupiter.params.provider.Arguments.of("long_unicode", "🔐".repeat(65)));
    }

    @Test
    void repeatedCreationReportsConflictWithoutReplacingPassword() throws Exception {
        var first = initialize("repeat_admin", "original-password", "original-password");
        assertThat(first.exitCode()).as(first.output()).isZero();
        String originalHash = passwordHash("repeat_admin");

        var repeated = initialize("  REPEAT_ADMIN  ", "replacement-password", "replacement-password");

        assertThat(repeated.exitCode()).isEqualTo(3);
        assertThat(repeated.output()).contains("ADMIN_ACCOUNT_CONFLICT")
                .doesNotContain("replacement-password", "original-password", originalHash, "INSERT", "Exception");
        assertThat(passwordHash("repeat_admin")).isEqualTo(originalHash);
    }

    @Test
    void normalUserIsNeverPromotedOrOverwritten() throws Exception {
        assertThat(initialize("user_fixture_setup", "fixture-password", "fixture-password").exitCode()).isZero();
        try (var connection = connect(); var statement = connection.prepareStatement(
                "INSERT INTO user_account (account, password_hash, role, status, auth_epoch) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, "existing_user");
            statement.setString(2, "existing-user-hash-must-remain");
            statement.setString(3, "user");
            statement.setString(4, "disabled");
            statement.setLong(5, 7);
            statement.executeUpdate();
        }

        var result = initialize(" EXISTING_USER ", "new-admin-password", "new-admin-password");

        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.output()).contains("ADMIN_ACCOUNT_CONFLICT");
        try (var connection = connect(); var statement = connection.prepareStatement(
                "SELECT role, status, auth_epoch, password_hash FROM user_account WHERE account = 'existing_user'")) {
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("role")).isEqualTo("user");
                assertThat(rows.getString("status")).isEqualTo("disabled");
                assertThat(rows.getLong("auth_epoch")).isEqualTo(7);
                assertThat(rows.getString("password_hash")).isEqualTo("existing-user-hash-must-remain");
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("validBoundaryCredentials")
    void unicodeAndWhitespacePasswordsAreStoredAndVerifiedWithoutModification(String account, String password)
            throws Exception {
        var result = initialize(account, password, password);

        assertThat(result.exitCode()).as(result.output()).isZero();
        String hash = passwordHash(account.strip().toLowerCase(java.util.Locale.ROOT));
        assertThat(hash).startsWith("{argon2}$argon2id$v=19$m=65536,t=3,p=1$");
        var decoder = org.springframework.security.crypto.argon2.Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        String encoded = hash.substring("{argon2}".length());
        assertThat(decoder.matches(password, encoded)).isTrue();
        assertThat(decoder.matches(password + "wrong", encoded)).isFalse();
        if (!password.equals(password.strip())) {
            assertThat(decoder.matches(password.strip(), encoded)).isFalse();
        }
        assertThat(result.output()).doesNotContain(password, hash);
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> validBoundaryCredentials() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("  Ab_1  ", "abcdefgh"),
                org.junit.jupiter.params.provider.Arguments.of("A".repeat(32), "p".repeat(64)),
                org.junit.jupiter.params.provider.Arguments.of("unicode_lower", "🔐".repeat(8)),
                org.junit.jupiter.params.provider.Arguments.of("unicode_upper", "🔐".repeat(64)),
                org.junit.jupiter.params.provider.Arguments.of("space_password", " 123456 "));
    }

    @Test
    void equalPasswordsHaveDifferentRandomSaltsAndMeasuredVerificationCost() throws Exception {
        String password = "shared-salt-test-password";
        assertThat(initialize("salt_first", password, password).exitCode()).isZero();
        assertThat(initialize("salt_second", password, password).exitCode()).isZero();
        String first = passwordHash("salt_first");
        String second = passwordHash("salt_second");
        assertThat(first).startsWith("{argon2}$argon2id$").isNotEqualTo(second);
        var decoder = org.springframework.security.crypto.argon2.Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        String encoded = first.substring("{argon2}".length());
        assertThat(decoder.matches(password, encoded)).isTrue();
        for (int sample = 0; sample < 5; sample++) {
            long start = System.nanoTime();
            boolean matched = decoder.matches(password, encoded);
            long elapsed = System.nanoTime() - start;
            assertThat(matched).isTrue();
            System.out.printf(java.util.Locale.ROOT, "ARGON2_VERIFY_SAMPLE_MS=%.2f%n", elapsed / 1_000_000.0);
        }
    }

    @Test
    void concurrentCommandsCreateOnlyOneAccountAndNeverReplaceTheWinner() throws Exception {
        assertThat(initialize("race_fixture_setup", "fixture-password", "fixture-password").exitCode()).isZero();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return initialize("race_admin", "first-race-password", "first-race-password");
            });
            var second = executor.submit(() -> {
                start.await();
                return initialize(" RACE_ADMIN ", "second-race-password", "second-race-password");
            });
            start.countDown();
            var firstResult = first.get(45, TimeUnit.SECONDS);
            var secondResult = second.get(45, TimeUnit.SECONDS);
            assertThat(List.of(firstResult.exitCode(), secondResult.exitCode())).containsExactlyInAnyOrder(0, 3);
            String hash = passwordHash("race_admin").substring("{argon2}".length());
            var decoder = org.springframework.security.crypto.argon2.Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
            assertThat(decoder.matches("first-race-password", hash)).isEqualTo(firstResult.exitCode() == 0);
            assertThat(decoder.matches("second-race-password", hash)).isEqualTo(secondResult.exitCode() == 0);
            try (var connection = connect(); var query = connection.createStatement();
                 var rows = query.executeQuery("SELECT COUNT(*) FROM user_account WHERE account = 'race_admin'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void extraInputIsRejectedInsteadOfSilentlyTruncatingCredentials() throws Exception {
        var result = execute("extra_input\nabcdefgh\nabcdefgh\nignored-secret\n".getBytes(StandardCharsets.UTF_8),
                java.util.Map.of(), List.of("--create-admin"));
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.output()).contains("ADMIN_INPUT_INVALID").doesNotContain("ADMIN_CREATED", "ignored-secret");
    }

    @Test
    void malformedUtf8DoesNotSilentlyBecomeADifferentPassword() throws Exception {
        var input = new java.io.ByteArrayOutputStream();
        input.write("invalid_utf8\n1234567".getBytes(StandardCharsets.UTF_8));
        input.write(0xff);
        input.write("\n1234567".getBytes(StandardCharsets.UTF_8));
        input.write(0xff);
        input.write('\n');

        var result = execute(input.toByteArray(), java.util.Map.of(), List.of("--create-admin"));

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.output()).contains("ADMIN_INPUT_INVALID").doesNotContain("ADMIN_CREATED", "1234567");
    }

    @Test
    void missingInputAndCredentialArgumentsAreRejected() throws Exception {
        var missing = execute("missing_input\nsecret-password\n".getBytes(StandardCharsets.UTF_8),
                java.util.Map.of(), List.of("--create-admin"));
        assertThat(missing.exitCode()).isEqualTo(2);
        assertThat(missing.output()).contains("ADMIN_INPUT_INVALID").doesNotContain("secret-password");
        var arguments = execute(new byte[0], java.util.Map.of(), List.of("--create-admin", "secret-password"));
        assertThat(arguments.exitCode()).isEqualTo(2);
        assertThat(arguments.output()).contains("ADMIN_INPUT_INVALID").doesNotContain("secret-password");
    }

    @Test
    void initializationDoesNotDependOnNacosOrRedis() throws Exception {
        int closedPort;
        try (var socket = new java.net.ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var result = execute("isolated_command\ncommand-password\ncommand-password\n"
                        .getBytes(StandardCharsets.UTF_8),
                java.util.Map.of("NACOS_SERVER_ADDR", "127.0.0.1:" + closedPort,
                        "SPRING_DATA_REDIS_PORT", Integer.toString(closedPort),
                        "SPRING_CLOUD_NACOS_CONFIG_TIMEOUT", "1000"), List.of("--create-admin"));
        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("ADMIN_CREATED: isolated_command")
                .doesNotContain("Nacos", "nacos", "Redis", "command-password", "Exception");
    }

    @Test
    void databaseFailureHasNonzeroExitAndDoesNotExposeConnectionSecrets() throws Exception {
        int closedPort;
        try (var socket = new java.net.ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var result = execute("database_failure\nsecret-password\nsecret-password\n".getBytes(StandardCharsets.UTF_8),
                java.util.Map.of("SPRING_DATASOURCE_URL", "jdbc:mysql://127.0.0.1:" + closedPort
                                + "/unavailable?connectTimeout=1000&socketTimeout=1000",
                        "SPRING_DATASOURCE_PASSWORD", "database-secret-not-for-output",
                        "SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT", "1000"), List.of("--create-admin"));
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.output()).contains("ADMIN_INITIALIZATION_FAILED")
                .doesNotContain("database-secret-not-for-output", "secret-password", "jdbc:", "Exception", "INSERT");
    }

    @Test
    void disabledMigrationsCannotSilentlyBypassSchemaVerification() throws Exception {
        assertThat(initialize("migration_fixture_setup", "fixture-password", "fixture-password").exitCode()).isZero();
        long before = accountCount();
        var result = execute("migration_disabled\nsecret-password\nsecret-password\n".getBytes(StandardCharsets.UTF_8),
                java.util.Map.of("SPRING_FLYWAY_ENABLED", "false"), List.of("--create-admin"));
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.output()).contains("ADMIN_INITIALIZATION_FAILED");
        assertThat(accountCount()).isEqualTo(before);
    }

    @Test
    void normalWebStartupDoesNotReadCredentialsOrCreateAnyAccount() throws Exception {
        assertThat(initialize("normal_startup_fixture", "fixture-password", "fixture-password").exitCode()).isZero();
        long before = accountCount();
        var output = Files.createTempFile(temporaryDirectory, "web-startup-", ".log");
        // This test checks the web/command boundary independently of the config center.
        var process = startApplication(List.of(), java.util.Map.of(
                "SPRING_CONFIG_LOCATION", "classpath:byte-judge-session.yml",
                "SPRING_CLOUD_NACOS_CONFIG_ENABLED", "false"), output);
        try {
            try (var input = process.outputWriter(StandardCharsets.UTF_8)) {
                input.write("must_not_be_created\nsecret-password\nsecret-password\n");
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (process.isAlive() && System.nanoTime() < deadline
                    && !Files.readString(output, StandardCharsets.UTF_8).contains("Started UserApplication")) {
                process.waitFor(100, TimeUnit.MILLISECONDS);
            }
            assertThat(Files.readString(output, StandardCharsets.UTF_8)).contains("Started UserApplication")
                    .doesNotContain("ADMIN_CREATED", "secret-password");
            assertThat(process.isAlive()).isTrue();
            assertThat(accountCount()).isEqualTo(before);
        } finally {
            stop(process);
        }
    }

    @Test
    void diagnosticLoggingCannotExposeBoundPasswordHashes() throws Exception {
        var result = execute("debug_logging\nsecret-debug-password\nsecret-debug-password\n"
                        .getBytes(StandardCharsets.UTF_8),
                java.util.Map.of("LOGGING_LEVEL_COM_JACK_BYTEJUDGE", "TRACE"), List.of("--create-admin"));
        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).doesNotContain("$argon2id$", "secret-debug-password", DATABASE.getPassword());
        var logs = temporaryDirectory.resolve("application-logs");
        if (Files.exists(logs)) {
            try (var paths = Files.walk(logs)) {
                for (var path : paths.filter(Files::isRegularFile).toList()) {
                    assertThat(Files.readString(path, StandardCharsets.UTF_8))
                            .doesNotContain("$argon2id$", "secret-debug-password", DATABASE.getPassword());
                }
            }
        }
    }

    @Test
    void controlledJsonInputPreservesNewlinesAndAllUnicodePasswordCharacters() throws Exception {
        String password = " abc\n\rdef\u0000🔐 ";
        String document = """
                {
                  "account": "json_admin",
                  "password": " abc\\n\\rdef\\u0000🔐 ",
                  "confirmation": " abc\\n\\rdef\\u0000🔐 "
                }
                """;
        var result = execute(document.getBytes(StandardCharsets.UTF_8), java.util.Map.of(), List.of("--create-admin"));
        assertThat(result.exitCode()).as(result.output()).isZero();
        var hash = passwordHash("json_admin");
        var decoder = org.springframework.security.crypto.argon2.Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        assertThat(decoder.matches(password, hash.substring("{argon2}".length()))).isTrue();
        assertThat(decoder.matches(password.replace("\n", ""), hash.substring("{argon2}".length()))).isFalse();
        assertThat(result.output()).contains("ADMIN_CREATED").doesNotContain(password, hash);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "{\"account\":\"json_role\",\"password\":\"secret-input\",\"confirmation\":\"secret-input\",\"role\":\"admin\"}",
            "{\"account\":\"json_duplicate\",\"password\":\"first-secret\",\"password\":\"other-secret\",\"confirmation\":\"other-secret\"}",
            "{\"account\":\"json_trailing\",\"password\":\"secret-input\",\"confirmation\":\"secret-input\"} {}",
            "{\"account\":\"json_number\",\"password\":12345678,\"confirmation\":12345678}",
            "{\"account\":\"json_missing\",\"password\":\"secret-input\"}",
            "{\"account\":\"json_broken\",\"password\":\"secret-input"
    })
    void malformedOrAmbiguousJsonIsRejectedWithoutParserDiagnostics(String document) throws Exception {
        var result = execute(document.getBytes(StandardCharsets.UTF_8), java.util.Map.of(), List.of("--create-admin"));
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.output()).contains("ADMIN_INPUT_INVALID")
                .doesNotContain("ADMIN_CREATED", "secret-input", "first-secret", "other-secret", "Exception");
    }

    private static long accountCount() throws Exception {
        try (var connection = connect(); var query = connection.createStatement();
             var rows = query.executeQuery("SELECT COUNT(*) FROM user_account")) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }

    private static String passwordHash(String account) throws Exception {
        try (var connection = connect(); var query = connection.prepareStatement(
                "SELECT password_hash FROM user_account WHERE account = ?")) {
            query.setString(1, account);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
    }

    private static CommandResult initialize(String account, String password, String confirmation) throws Exception {
        return execute((account + "\n" + password + "\n" + confirmation + "\n").getBytes(StandardCharsets.UTF_8),
                java.util.Map.of(), List.of("--create-admin"));
    }

    private static CommandResult execute(byte[] input, java.util.Map<String, String> overrides, List<String> options)
            throws Exception {
        var output = Files.createTempFile(temporaryDirectory, "command-", ".log");
        var process = startApplication(options, overrides, output);
        try {
            try (var stdin = process.getOutputStream()) {
                stdin.write(input);
            }
            // Bounded by the command's own work: Flyway plus Argon2 on a loaded CI/desktop host.
            assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("Initialization must exit without starting a server")
                    .isTrue();
            return new CommandResult(process.exitValue(), Files.readString(output, StandardCharsets.UTF_8));
        } finally {
            stop(process);
        }
    }

    private static Process startApplication(List<String> options, java.util.Map<String, String> overrides, Path output)
            throws Exception {
        var arguments = Files.createTempFile(temporaryDirectory, "java-", ".args");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"))
                .replace('\\', '/');
        Files.writeString(arguments, "-Xmx256m\n-cp\n\"" + classpath + "\"\n"
                + "com.jack.bytejudge.user.UserApplication\n" + String.join("\n", options) + "\n",
                StandardCharsets.UTF_8);
        var builder = new ProcessBuilder(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "@" + arguments));
        var environment = builder.environment();
        environment.put("SPRING_PROFILES_ACTIVE", "admin-command-test");
        environment.put("BYTE_JUDGE_INTERNAL_GATEWAY_CREDENTIAL",
                "admin-command-test-internal-credential-0123456789");
        environment.put("BYTE_JUDGE_WEB_INGRESS", "false");
        environment.put("BYTE_JUDGE_AUDIT_WORKER_ID", "0");
        environment.put("BYTE_JUDGE_AUDIT_DATACENTER_ID", "0");
        environment.put("BYTE_JUDGE_CONFIG_CONTRACT", "auth-infra-v1");
        environment.put("SPRING_DATASOURCE_URL", DATABASE.getJdbcUrl());
        environment.put("SPRING_DATASOURCE_USERNAME", DATABASE.getUsername());
        environment.put("SPRING_DATASOURCE_PASSWORD", DATABASE.getPassword());
        environment.put("SPRING_CLOUD_NACOS_DISCOVERY_ENABLED", "false");
        environment.put("SPRING_CLOUD_DISCOVERY_ENABLED", "false");
        environment.put("SPRING_DATA_REDIS_HOST", REDIS.getHost());
        environment.put("SPRING_DATA_REDIS_PORT", Integer.toString(REDIS.getMappedPort(6379)));
        environment.put("SPRING_DATA_REDIS_PASSWORD", REDIS_PASSWORD);
        environment.put("SPRING_DATA_REDIS_CONNECT_TIMEOUT", "5s");
        environment.put("SPRING_DATA_REDIS_TIMEOUT", "10s");
        environment.put("SERVER_PORT", "0");
        environment.put("MANAGEMENT_SERVER_PORT", "0");
        environment.put("BYTE_JUDGE_LOG_DIR", temporaryDirectory.resolve("application-logs").toString());
        environment.putAll(overrides);
        return builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
    }

    private static void stop(Process process) throws InterruptedException {
        if (process.isAlive()) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }

    private record CommandResult(int exitCode, String output) { }
}
