package com.jack.bytejudge.user.config;

import com.jack.bytejudge.common.core.response.ApiError;
import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.common.web.RequestLogFilter;
import com.jack.bytejudge.common.web.SecurityErrorWriter;
import com.jack.bytejudge.common.web.SessionConfiguration;
import com.jack.bytejudge.common.web.WebProtocolConfiguration;
import com.jack.bytejudge.user.auth.application.Authenticator;
import com.jack.bytejudge.user.auth.application.IdentityRevalidator;
import com.jack.bytejudge.user.auth.application.port.AccountAuthority;
import com.jack.bytejudge.user.auth.application.port.AccountLookup;
import com.jack.bytejudge.user.auth.application.port.AuthAudit;
import com.jack.bytejudge.user.auth.application.port.LoginAttemptLimiter;
import com.jack.bytejudge.user.auth.application.port.PasswordMatcher;
import com.jack.bytejudge.user.auth.domain.AccountDisabledException;
import com.jack.bytejudge.user.auth.domain.InvalidCredentialsException;
import com.jack.bytejudge.user.auth.domain.model.AuthAuditEntry;
import com.jack.bytejudge.user.auth.domain.model.AuthEvent;
import com.jack.bytejudge.user.auth.domain.model.AuthOutcome;
import com.jack.bytejudge.user.auth.infrastructure.persistence.mapper.AuthAuditMapper;
import com.jack.bytejudge.user.auth.infrastructure.persistence.mapper.UserAccountMapper;
import com.jack.bytejudge.user.auth.infrastructure.persistence.repository.MybatisAccountAuthority;
import com.jack.bytejudge.user.auth.infrastructure.persistence.repository.MybatisAccountLookup;
import com.jack.bytejudge.user.auth.infrastructure.persistence.repository.MybatisAuthAudit;
import com.jack.bytejudge.user.auth.interfaces.http.IdentityRevalidationFilter;
import com.jack.bytejudge.user.auth.interfaces.internal.InternalCallGuard;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import java.nio.CharBuffer;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@Import({SessionConfiguration.class, WebProtocolConfiguration.class})
@MapperScan(basePackageClasses = UserAccountMapper.class)
public class WebSecurityConfiguration {
    @Bean
    SecurityFilterChain userSecurity(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
                                     SecurityContextRepository securityContextRepository,
                                     IdentityRevalidator identityRevalidator,
                                     AuthAudit audit) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/internal/v1/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAuthority("ROLE_admin")
                        .requestMatchers("/api/v1/users/me").authenticated()
                        .anyRequest().permitAll())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(SecurityErrorWriter.authenticationEntryPoint())
                        .accessDeniedHandler((request, response, failure) -> {
                            // Permission denials are audited by the owning service, without request details.
                            Authentication authentication = org.springframework.security.core.context
                                    .SecurityContextHolder.getContext().getAuthentication();
                            if (authentication != null
                                    && authentication.getPrincipal() instanceof SessionIdentity identity
                                    && request.getRequestURI().startsWith("/api/v1/admin/")) {
                                audit.record(new AuthAuditEntry(AuthEvent.ADMIN_ACCESS,
                                        AuthOutcome.DENIED, identity.userId(), identity.account(), "ROLE",
                                        RequestLogFilter.requestId(request)));
                            }
                            SecurityErrorWriter.writeError(request, response,
                                    failure instanceof CsrfException ? ApiError.CSRF_INVALID : ApiError.ACCESS_DENIED);
                        }))
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                        // Internal calls authenticate with the dedicated service identity instead.
                        .ignoringRequestMatchers("/internal/v1/**")
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                // Created inline so Spring Boot does not also register it as a servlet filter outside
                // the security chain.
                .addFilterBefore(new IdentityRevalidationFilter(identityRevalidator, audit),
                        AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        // Explicit, so login controller and filter chain share the HTTP-session repository.
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    AuthenticationManager authenticationManager(Authenticator authenticator) {
        AuthenticationProvider provider = new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                try {
                    // The controller resolves the trusted source address and puts it in the token
                    // details; requestId stays the audit correlation value.
                    String sourceAddress = authentication.getDetails() instanceof String address ? address : "";
                    SessionIdentity identity = authenticator.authenticate(
                            (String) authentication.getPrincipal(),
                            ((String) authentication.getCredentials()).toCharArray(),
                            RequestLogFilter.currentRequestId(), sourceAddress);
                    return UsernamePasswordAuthenticationToken.authenticated(identity, null,
                            List.of(new SimpleGrantedAuthority(SessionIdentity.ROLE_PREFIX + identity.role())));
                } catch (InvalidCredentialsException invalid) {
                    throw new BadCredentialsException(invalid.getMessage());
                } catch (AccountDisabledException disabled) {
                    throw new DisabledException(disabled.getMessage());
                }
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };
        return new ProviderManager(List.of(provider));
    }

    @Bean
    Authenticator authenticator(AccountLookup accounts, PasswordMatcher passwords, AuthAudit audit,
                                LoginAttemptLimiter loginAttempts) {
        return new Authenticator(accounts, passwords, audit, loginAttempts);
    }

    @Bean
    AccountLookup accountLookup(UserAccountMapper mapper) {
        return new MybatisAccountLookup(mapper);
    }

    @Bean
    AccountAuthority accountAuthority(UserAccountMapper mapper) {
        return new MybatisAccountAuthority(mapper);
    }

    @Bean
    IdentityRevalidator identityRevalidator(AccountAuthority accounts) {
        return new IdentityRevalidator(accounts);
    }

    @Bean
    AuthAudit authAudit(AuthAuditMapper mapper) {
        return new MybatisAuthAudit(mapper);
    }

    @Bean
    InternalCallGuard internalCallGuard(
            @Value("${byte-judge.internal.gateway-credential}") String gatewayCredential) {
        return new InternalCallGuard(gatewayCredential);
    }

    @Bean
    PasswordMatcher passwordMatcher(PasswordEncoder encoder) {
        return (raw, hash) -> encoder.matches(CharBuffer.wrap(raw), hash);
    }
}
