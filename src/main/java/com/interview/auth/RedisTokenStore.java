package com.interview.auth;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Redis 저장소. 연결은 Spring Boot 의 spring.redis.* 설정을 따른다.
 * dev/prod 는 Sentinel(spring.redis.sentinel.master / nodes)로 붙는다 (application-dev.yml, docs/database.md).
 */
@Component
@ConditionalOnProperty(name = "app.token-store.type", havingValue = "redis")
@RequiredArgsConstructor
public class RedisTokenStore implements TokenStore {

    private static final String PREFIX = "iv:refresh:";

    private final StringRedisTemplate redis;

    @Override
    public void save(String refreshToken, Long userId, long ttlSeconds) {
        redis.opsForValue().set(PREFIX + refreshToken, String.valueOf(userId), ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public Long findUserId(String refreshToken) {
        if (refreshToken == null) {
            return null;
        }
        String value = redis.opsForValue().get(PREFIX + refreshToken);
        return value == null ? null : Long.valueOf(value);
    }

    @Override
    public void remove(String refreshToken) {
        if (refreshToken != null) {
            redis.delete(PREFIX + refreshToken);
        }
    }
}
