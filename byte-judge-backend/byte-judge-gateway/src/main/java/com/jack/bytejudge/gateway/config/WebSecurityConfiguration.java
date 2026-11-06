package com.jack.bytejudge.gateway.config;

import com.jack.bytejudge.common.web.SessionConfiguration;
import com.jack.bytejudge.common.web.WebProtocolConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;

@Configuration(proxyBeanMethods = false)
@Import({SessionConfiguration.class, WebProtocolConfiguration.class})
public class WebSecurityConfiguration {
    @Bean
    SecurityFilterChain gatewaySecurity(HttpSecurity http) throws Exception {
        // The user service owns login/logout and CSRF writes. Do not load or save the
        // pre-login session in an outer proxy request while the owner rotates its ID.
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }
}
