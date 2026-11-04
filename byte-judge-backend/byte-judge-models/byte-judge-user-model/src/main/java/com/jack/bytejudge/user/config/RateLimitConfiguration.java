package com.jack.bytejudge.user.config;

import com.jack.bytejudge.user.auth.application.port.LoginAttemptLimiter;
import com.jack.bytejudge.user.auth.infrastructure.ratelimit.LoginRateLimitProperties;
import com.jack.bytejudge.user.auth.infrastructure.ratelimit.RedisLoginAttemptLimiter;
import com.jack.bytejudge.user.auth.interfaces.http.ClientAddressResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LoginRateLimitProperties.class)
public class RateLimitConfiguration {
    @Bean
    LoginAttemptLimiter loginAttemptLimiter(StringRedisTemplate redis, LoginRateLimitProperties properties) {
        return new RedisLoginAttemptLimiter(redis, properties);
    }

    @Bean
    ClientAddressResolver clientAddressResolver(
            @Value("${byte-judge.web.trusted-proxies:127.0.0.1,0:0:0:0:0:0:0:1}") String trustedProxies) {
        return new ClientAddressResolver(trustedProxies);
    }
}
