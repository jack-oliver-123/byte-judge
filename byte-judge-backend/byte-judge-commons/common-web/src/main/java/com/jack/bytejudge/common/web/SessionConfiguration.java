package com.jack.bytejudge.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.MapSession;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.RedisSessionMapper;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.util.Map;
import java.util.function.BiFunction;

@Configuration(proxyBeanMethods = false)
public class SessionConfiguration {
    @Bean
    CookieSerializer sessionCookieSerializer(@Value("${byte-judge.session.cookie-secure:true}") boolean secure) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("BJSESSION");
        serializer.setCookiePath("/");
        // The internal verification call forwards the cookie value as the session identifier; the
        // default Base64 encoding would make that value unusable with the indexed repository.
        serializer.setUseBase64Encoding(false);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite("Lax");
        return serializer;
    }

    @Bean
    RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        // Shared sessions contain only JDK and Spring Security/CSRF types, never a service's DTO or entity.
        return new JdkSerializationRedisSerializer();
    }

    /**
     * Reads an incomplete Redis hash as an invalid session. After a concurrent logout or expiry, a
     * request that already loaded the session can still write an attribute delta; Redis recreates
     * the hash without the required creation/last-access fields. That is a missing session, not a
     * server failure, and it must never be mistaken for a valid identity. The delegate's only
     * {@link IllegalStateException} is the missing-required-field path; connection failures surface
     * as data-access exceptions and are not caught here, so unreachable storage still fails
     * protected requests.
     */
    @Bean
    @Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
    SessionRepositoryCustomizer<RedisIndexedSessionRepository> safeSessionRepositoryCustomizer() {
        return repository -> repository.setRedisSessionMapper(new MissingFieldsSafeSessionMapper());
    }

    static final class MissingFieldsSafeSessionMapper
            implements BiFunction<String, Map<String, Object>, MapSession> {
        private final RedisSessionMapper delegate = new RedisSessionMapper();

        @Override
        public MapSession apply(String sessionId, Map<String, Object> entries) {
            try {
                return delegate.apply(sessionId, entries);
            } catch (IllegalStateException missingRequiredField) {
                return null;
            }
        }
    }
}
