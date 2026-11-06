package com.jack.bytejudge.user.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.jack.bytejudge.user.account.application.AdministratorCreator;
import com.jack.bytejudge.user.account.application.port.PasswordHasher;
import com.jack.bytejudge.user.account.domain.repository.AdminAccountRepository;
import com.jack.bytejudge.user.account.infrastructure.persistence.mapper.AdminAccountMapper;
import com.jack.bytejudge.user.account.infrastructure.persistence.repository.MybatisAdminAccountRepository;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.CharBuffer;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = AdminAccountMapper.class)
public class AccountConfiguration {
    @Bean
    ConfigurationCustomizer protectSqlParameters() {
        // SQL parameter logging would expose password hashes even when application logs are safe.
        return configuration -> configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
    }

    @Bean
    PasswordEncoder accountPasswordEncoder() {
        var argon2 = new Argon2PasswordEncoder(16, 32, 1, 65_536, 3);
        return new DelegatingPasswordEncoder("argon2", Map.of("argon2", argon2));
    }

    @Bean
    PasswordHasher passwordHasher(PasswordEncoder encoder) {
        return password -> encoder.encode(CharBuffer.wrap(password));
    }

    @Bean
    AdminAccountRepository adminAccountRepository(AdminAccountMapper mapper) {
        return new MybatisAdminAccountRepository(mapper);
    }

    @Bean
    AdministratorCreator administratorCreator(AdminAccountRepository accounts, PasswordHasher passwords) {
        return new AdministratorCreator(accounts, passwords);
    }
}
