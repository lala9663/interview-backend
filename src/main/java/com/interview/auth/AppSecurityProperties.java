package com.interview.auth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** app.security.* 설정. 값은 application*.yml 과 환경변수에서 온다 (README 의 환경변수 표). */
@ConfigurationProperties(prefix = "app.security")
@Getter
@Setter
public class AppSecurityProperties {

    public static final String DEV_INTERNAL_KEY = "dev-internal-key";
    public static final String DEV_ROOM_TICKET_SECRET = "dev-room-ticket-secret-change-me-32bytes!!";

    /** access 토큰 서명 키(HS256, 32바이트 이상). 환경변수 JWT_SECRET. */
    private String jwtSecret;
    private long accessTtlSeconds = 1800;
    private long refreshTtlSeconds = 1209600;

    /** /internal/v1/** 의 X-Internal-Key. 환경변수 INTERNAL_API_KEY. */
    private String internalKey = DEV_INTERNAL_KEY;

    /** 방 입장권 서명 키(HS256). Agent 와 공유한다. 환경변수 ROOM_TICKET_SECRET. */
    private String roomTicketSecret = DEV_ROOM_TICKET_SECRET;
    private long roomTicketTtlSeconds = 300;

    private List<String> corsAllowedOrigins = new ArrayList<String>();
}
