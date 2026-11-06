package com.jack.bytejudge.user.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test-only fault injection for the dependency-failure slice. It exists only on the test classpath
 * and under the auth-http-test profile, so the shipped artifact exposes no fault switch.
 *
 * <p>The internal verification delay is controlled by a file rather than a request header because
 * the gateway's private verification call intentionally forwards no browser headers. The unknown
 * exception is triggered by a marker header on an unauthenticated public path so no session or
 * storage access masks the failure.
 */
@Configuration(proxyBeanMethods = false)
@Profile("auth-http-test")
class Auth12FaultProbe {
    private static final String FAULT_HEADER = "X-Auth12-Fault";
    private static final String DELAY_FILE = "internal-verify-delay-ms";
    private static final String INTERNAL_VERIFY_PATH = "/internal/v1/identity/verify";

    @Bean
    FilterRegistrationBean<Filter> auth12FaultFilter(
            @Value("${byte-judge.auth12.probe-dir}") String directory) {
        var registration = new FilterRegistrationBean<Filter>(new FaultFilter(Path.of(directory)));
        registration.addUrlPatterns("/*");
        registration.setOrder(-80);
        return registration;
    }

    private static final class FaultFilter implements Filter {
        private static final long MAXIMUM_DELAY_MILLIS = 30_000;

        private final Path directory;

        private FaultFilter(Path directory) {
            this.directory = directory;
        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                             FilterChain chain) throws IOException, ServletException {
            var request = (HttpServletRequest) servletRequest;
            var response = (HttpServletResponse) servletResponse;
            if (INTERNAL_VERIFY_PATH.equals(request.getRequestURI())) {
                delayInternalVerification();
            } else if ("explode".equals(request.getHeader(FAULT_HEADER))) {
                throw new IllegalStateException("test-only injected failure");
            }
            chain.doFilter(request, response);
        }

        private void delayInternalVerification() {
            Path file = this.directory.resolve(DELAY_FILE);
            if (!Files.isRegularFile(file)) {
                return;
            }
            try {
                long millis = Long.parseLong(Files.readString(file, StandardCharsets.UTF_8).strip());
                if (millis > 0) {
                    Thread.sleep(Math.min(millis, MAXIMUM_DELAY_MILLIS));
                }
            } catch (IOException | NumberFormatException unreadable) {
                // A malformed or removed control file simply means no injected delay.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
