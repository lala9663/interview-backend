package com.interview.auth;

import java.nio.charset.StandardCharsets;

import javax.annotation.PostConstruct;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 시작할 때 비밀키 설정을 검사한다. prod 에서는 개발용 기본값으로 뜨지 못하게 막는다. */
@Component
@RequiredArgsConstructor
public class SecretsGuard {

    private final AppSecurityProperties props;
    private final Environment env;

    @PostConstruct
    public void check() {
        requireLength("JWT_SECRET(app.security.jwt-secret)", props.getJwtSecret());
        requireLength("ROOM_TICKET_SECRET(app.security.room-ticket-secret)", props.getRoomTicketSecret());
        if (props.getInternalKey() == null || props.getInternalKey().trim().isEmpty()) {
            throw new IllegalStateException("INTERNAL_API_KEY(app.security.internal-key)가 비어 있습니다.");
        }
        if (env.acceptsProfiles(Profiles.of("prod"))) {
            if (AppSecurityProperties.DEV_INTERNAL_KEY.equals(props.getInternalKey())
                    || AppSecurityProperties.DEV_ROOM_TICKET_SECRET.equals(props.getRoomTicketSecret())) {
                throw new IllegalStateException("prod 프로파일에서는 INTERNAL_API_KEY, ROOM_TICKET_SECRET 을 개발용 기본값으로 둘 수 없습니다.");
            }
        }
    }

    private void requireLength(String name, String value) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(name + " 는 32바이트 이상이어야 합니다(HS256). 환경변수를 확인하세요.");
        }
    }
}
