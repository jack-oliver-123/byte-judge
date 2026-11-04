package com.jack.bytejudge.user.config;

import com.jack.bytejudge.common.web.SecurityErrorWriter;
import com.jack.bytejudge.common.web.SessionConfiguration;
import com.jack.bytejudge.common.web.WebProtocolConfiguration;
import com.jack.bytejudge.user.auth.application.Authenticator;
import com.jack.bytejudge.user.auth.application.port.AccountLookup;
import com.jack.bytejudge.user.auth.application.port.PasswordMatcher;
import com.jack.bytejudge.user.auth.domain.AccountDisabledException;
import com.jack.bytejudge.user.auth.domain.InvalidCredentialsException;
import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.user.auth.infrastructure.persistence.mapper.UserAccountMapper;
import com.jack.bytejudge.user.auth.infrastructure.persistence.repository.MybatisAccountLookup;
import org.mybatis.spring.annotation.MapperScan;
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
                                     SecurityContextRepository securityContextRepository) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/users/me").authenticated()
                        .anyRequest().permitAll())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(SecurityErrorWriter.authenticationEntryPoint())
                        .accessDeniedHandler(SecurityErrorWriter.accessDeniedHandler()))
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
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
                    SessionIdentity identity = authenticator.authenticate(
                            (String) authentication.getPrincipal(),
                            ((String) authentication.getCredentials()).toCharArray());
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
    Authenticator authenticator(AccountLookup accounts, PasswordMatcher passwords) {
        return new Authenticator(accounts, passwords);
    }

    @Bean
    AccountLookup accountLookup(UserAccountMapper mapper) {
        return new MybatisAccountLookup(mapper);
    }

    @Bean
    PasswordMatcher passwordMatcher(PasswordEncoder encoder) {
        return (raw, hash) -> encoder.matches(CharBuffer.wrap(raw), hash);
    }
}
