package com.jack.bytejudge.user.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Test-only coordination for a request that has already loaded its session and passed
 * authentication. It exists only on the test classpath and under the auth-http-test profile, so the
 * shipped artifact has no timing hook. Order -90 runs it after the Spring Security filter chain
 * (order -100), so the request keeps the identity it loaded even if the session is revoked while it
 * waits; the servlet response is still saved by Spring Session afterwards.
 */
@Configuration(proxyBeanMethods = false)
@Profile("auth-http-test")
class Auth9SessionRaceProbe {
    private static final String MODE_HEADER = "X-Auth9-Race";
    private static final String TOKEN_HEADER = "X-Auth9-Race-Token";

    @Bean
    FilterRegistrationBean<Filter> auth9SessionRaceFilter(
            @Value("${byte-judge.auth9.race-dir}") String raceDirectory) {
        var registration = new FilterRegistrationBean<Filter>(new RaceFilter(Path.of(raceDirectory)));
        registration.addUrlPatterns("/api/v1/users/me");
        registration.setOrder(-90);
        return registration;
    }

    private static final class RaceFilter implements Filter {
        private final Path directory;

        private RaceFilter(Path directory) {
            this.directory = directory;
        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                             FilterChain chain) throws IOException, ServletException {
            var request = (HttpServletRequest) servletRequest;
            var response = (HttpServletResponse) servletResponse;
            String mode = request.getHeader(MODE_HEADER);
            if (mode == null) {
                chain.doFilter(request, response);
                return;
            }
            String token = request.getHeader(TOKEN_HEADER);
            if (!("hold".equals(mode) || "touch".equals(mode))
                    || token == null || !token.matches("[A-Za-z0-9-]{1,64}")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            HttpSession session = request.getSession(false);
            if (session == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            Files.createDirectories(this.directory);
            Files.writeString(this.directory.resolve(token + ".loaded"), session.getId(),
                    StandardCharsets.UTF_8);
            Path release = this.directory.resolve(token + ".release");
            long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
            while (!Files.isRegularFile(release)) {
                if (System.nanoTime() > deadline) {
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "race release timeout");
                    return;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "race interrupted");
                    return;
                }
            }
            if ("touch".equals(mode)) {
                // Forces a real attribute delta so this request attempts to write the old session
                // after the revocation completed, instead of relying on a no-op save.
                session.setAttribute("auth9RaceTouch", token);
            }
            chain.doFilter(request, response);
        }
    }
}
