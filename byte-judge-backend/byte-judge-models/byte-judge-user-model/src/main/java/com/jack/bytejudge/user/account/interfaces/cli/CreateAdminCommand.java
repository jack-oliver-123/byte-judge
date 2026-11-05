package com.jack.bytejudge.user.account.interfaces.cli;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.jack.bytejudge.user.account.application.AdministratorCreator;
import com.jack.bytejudge.user.account.domain.AccountInputException;
import com.jack.bytejudge.user.account.domain.AccountConflictException;
import com.jack.bytejudge.user.config.AccountConfiguration;
import org.flywaydb.core.Flyway;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** A command-only composition root; it never scans or starts the web application. */
public final class CreateAdminCommand {
    private CreateAdminCommand() { }

    public static int run() {
        char[] password = null;
        char[] confirmation = null;
        try {
            var console = System.console();
            var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8.newDecoder()));
            String account;
            if (console != null) {
                account = console.readLine("Account: ");
                password = console.readPassword("Password: ");
                confirmation = console.readPassword("Confirm password: ");
            } else {
                String firstLine = input.readLine();
                if (firstLine != null && firstLine.stripLeading().startsWith("{")) {
                    var credentials = readJson(firstLine, input);
                    account = credentials.account();
                    password = credentials.password();
                    confirmation = credentials.confirmation();
                } else {
                    account = firstLine;
                    password = readPassword(input);
                    confirmation = readPassword(input);
                    if (input.read() != -1) {
                        throw new AccountInputException("Expected exactly three UTF-8 input lines or a JSON object.");
                    }
                }
            }
            if (account == null || password == null || confirmation == null) {
                System.err.println("ADMIN_INPUT_INVALID: account, password and confirmation are required.");
                return 2;
            }
            try (var context = new SpringApplicationBuilder(Bootstrap.class)
                    .web(WebApplicationType.NONE)
                    .bannerMode(Banner.Mode.OFF)
                    .logStartupInfo(false)
                    // Command invariants must outrank imported logging configuration.
                    .run("--spring.config.name=admin-command", "--spring.cloud.nacos.config.enabled=false",
                            "--logging.level.root=OFF", "--mybatis-plus.global-config.banner=false")) {
                // A disabled migration must not silently create an account against an unverified schema.
                context.getBean(Flyway.class);
                String normalized = context.getBean(AdministratorCreator.class).create(account, password, confirmation);
                System.out.println("ADMIN_CREATED: " + normalized);
                return 0;
            }
        } catch (IOException exception) {
            System.err.println("ADMIN_INPUT_INVALID: input must use valid UTF-8 and include all credentials.");
            return 2;
        } catch (AccountConflictException exception) {
            System.err.println("ADMIN_ACCOUNT_CONFLICT: " + exception.getMessage());
            return 3;
        } catch (AccountInputException exception) {
            System.err.println("ADMIN_INPUT_INVALID: " + exception.getMessage());
            return 2;
        } catch (Exception exception) {
            System.err.println("ADMIN_INITIALIZATION_FAILED: check database configuration and schema availability.");
            return 1;
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            if (confirmation != null) {
                Arrays.fill(confirmation, '\0');
            }
        }
    }

    private static Credentials readJson(String firstLine, BufferedReader input) throws IOException {
        var document = new StringWriter();
        document.write(firstLine);
        document.write('\n');
        input.transferTo(document);
        try {
            var mapper = JsonMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();
            Map<?, ?> fields = mapper.readValue(document.toString(), Map.class);
            if (!fields.keySet().equals(Set.of("account", "password", "confirmation"))
                    || !(fields.get("account") instanceof String account)
                    || !(fields.get("password") instanceof String password)
                    || !(fields.get("confirmation") instanceof String confirmation)) {
                throw new AccountInputException("JSON must contain only account, password and confirmation strings.");
            }
            return new Credentials(account, password.toCharArray(), confirmation.toCharArray());
        } catch (JacksonException exception) {
            // Parser diagnostics may contain credential values; never forward the parser message.
            throw new AccountInputException("Invalid credential JSON.");
        }
    }

    private record Credentials(String account, char[] password, char[] confirmation) { }

    private static char[] readPassword(BufferedReader input) throws IOException {
        String line = input.readLine();
        return line == null ? null : line.toCharArray();
    }

    // Explicit sources are processed by Spring but are not components in normal service scanning.
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class})
    @Import(AccountConfiguration.class)
    static class Bootstrap { }
}
