package com.interview.auth;

/**
 * refresh 토큰 저장소. 교체 지점: app.token-store.type = memory | redis
 * - memory: {@link InMemoryTokenStore} (local, 서버 한 대)
 * - redis : {@link RedisTokenStore} (dev/prod, Sentinel)
 */
public interface TokenStore {

    void save(String refreshToken, Long userId, long ttlSeconds);

    /** 없거나 만료면 null. */
    Long findUserId(String refreshToken);

    void remove(String refreshToken);
}
