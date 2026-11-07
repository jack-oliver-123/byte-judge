package com.jack.bytejudge.problem.config;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.jack.bytejudge.common.core.response.ApiError;
import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.common.web.*;
import com.jack.bytejudge.problem.admin.application.port.IdentityVerifier;
import com.jack.bytejudge.problem.admin.application.port.ProblemAudit;
import com.jack.bytejudge.problem.admin.domain.model.*;
import com.jack.bytejudge.problem.admin.infrastructure.persistence.mapper.ProblemAuditMapper;
import com.jack.bytejudge.problem.admin.infrastructure.persistence.repository.MybatisProblemAudit;
import com.jack.bytejudge.problem.admin.interfaces.http.IdentityRevalidationFilter;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;

@Configuration(proxyBeanMethods = false)
@Import({SessionConfiguration.class, WebProtocolConfiguration.class})
@MapperScan(basePackageClasses = ProblemAuditMapper.class)
public class ProblemSecurityConfiguration {
    @Bean
    SecurityFilterChain problemSecurity(HttpSecurity http, CsrfTokenRepository csrfTokens,
                                        SecurityContextRepository contexts,
                                        IdentityVerifier verifier, ProblemAudit audit) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/admin/**").hasAuthority("ROLE_admin")
                        .anyRequest().permitAll())
                .securityContext(context -> context.securityContextRepository(contexts))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(SecurityErrorWriter.authenticationEntryPoint())
                        .accessDeniedHandler((request, response, failure) -> {
                            var authentication = org.springframework.security.core.context
                                    .SecurityContextHolder.getContext().getAuthentication();
                            if (authentication != null && authentication.getPrincipal() instanceof SessionIdentity identity
                                    && ProtectedPaths.isAdministrative(ProtectedPaths.routingPath(request))) {
                                audit.record(new ProblemAuditEntry(ProblemAuditEvent.ADMIN_ACCESS,
                                        ProblemAuditOutcome.DENIED, identity.userId(), identity.account(),
                                        failure instanceof CsrfException ? "CSRF" : "ROLE", null,
                                        RequestLogFilter.requestId(request)));
                            }
                            SecurityErrorWriter.writeError(request, response,
                                    failure instanceof CsrfException ? ApiError.CSRF_INVALID : ApiError.ACCESS_DENIED);
                        }))
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterBefore(new IdentityRevalidationFilter(verifier, audit), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }

    @Bean
    SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean
    IdentifierGenerator problemIdentifiers(@Value("${byte-judge.problem.worker-id}") long workerId,
                                           @Value("${byte-judge.problem.datacenter-id}") long datacenterId) {
        return new DefaultIdentifierGenerator(workerId, datacenterId);
    }

    @Bean
    ProblemAudit problemAudit(ProblemAuditMapper mapper, IdentifierGenerator identifiers) {
        return new MybatisProblemAudit(mapper, identifiers);
    }
}
