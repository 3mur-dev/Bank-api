package com.omar.bankapi.config;

import com.omar.bankapi.service.InMemoryLoginRateLimiter;
import com.omar.bankapi.service.LoginRateLimiter;
import com.omar.bankapi.service.RedisLoginRateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(LoginRateLimitProperties.class)
public class LoginRateLimitConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.login-rate-limit.backend", havingValue = "redis")
    public LoginRateLimiter redisLoginRateLimiter(
            StringRedisTemplate redisTemplate,
            LoginRateLimitProperties properties
    ) {
        return new RedisLoginRateLimiter(redisTemplate, properties);
    }

    @Bean
    @ConditionalOnProperty(
            name = "app.login-rate-limit.backend",
            havingValue = "memory",
            matchIfMissing = true
    )
    public LoginRateLimiter inMemoryLoginRateLimiter(LoginRateLimitProperties properties) {
        return new InMemoryLoginRateLimiter(properties);
    }
}
