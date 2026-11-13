package com.interview.auth;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 메모리 저장소. 서버를 다시 띄우면 refresh 토큰이 사라진다(local 용). */
@Component
@ConditionalOnProperty(name = "app.token-store.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryTokenStore implements TokenStore {

    private final Map<String, long[]> tokens = new ConcurrentHashMap<String, long[]>();

    @Override
    public void save(String refreshToken, Long userId, long ttlSeconds) {
        purgeExpired();
        tokens.put(refreshToken, new long[] { userId, System.currentTimeMillis() + ttlSeconds * 1000L });
    }

    @Override
    public Long findUserId(String refreshToken) {
        if (refreshToken == null) {
            return null;
        }
        long[] entry = tokens.get(refreshToken);
        if (entry == null) {
            return null;
        }
        if (entry[1] < System.currentTimeMillis()) {
            tokens.remove(refreshToken);
            return null;
        }
        return entry[0];
    }

    @Override
    public void remove(String refreshToken) {
        if (refreshToken != null) {
            tokens.remove(refreshToken);
        }
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, long[]>> it = tokens.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue()[1] < now) {
                it.remove();
            }
        }
    }
}
