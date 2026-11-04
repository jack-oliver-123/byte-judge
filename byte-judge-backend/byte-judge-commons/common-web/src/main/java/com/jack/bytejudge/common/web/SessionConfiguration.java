package com.jack.bytejudge.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration(proxyBeanMethods = false)
public class SessionConfiguration {
    @Bean
    CookieSerializer sessionCookieSerializer(@Value("${byte-judge.session.cookie-secure:true}") boolean secure) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("BJSESSION");
        serializer.setCookiePath("/");
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
}
