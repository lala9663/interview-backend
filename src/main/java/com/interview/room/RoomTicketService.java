package com.interview.room;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.springframework.stereotype.Service;

import com.interview.agent.AgentProperties;
import com.interview.auth.AppSecurityProperties;
import com.interview.auth.CurrentUser;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

/**
 * 방 입장권(JWT, HS256, 기본 5분). 비밀키(ROOM_TICKET_SECRET)는 Agent 와 공유한다.
 * 클레임: sub(사용자 id), name, roomId, audio(boolean), exp — API 명세 2.7.
 */
@Service
@RequiredArgsConstructor
public class RoomTicketService {

    private final AppSecurityProperties securityProps;
    private final AgentProperties agentProps;

    public RoomDtos.Ticket issue(Long roomId, CurrentUser user, boolean audio) {
        long ttl = securityProps.getRoomTicketTtlSeconds();
        Date now = new Date();
        String jwt = Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("name", user.getName())
                .claim("roomId", roomId)
                .claim("audio", audio)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttl * 1000L))
                .signWith(Keys.hmacShaKeyFor(securityProps.getRoomTicketSecret().getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();
        String base = agentProps.getWsBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return new RoomDtos.Ticket(jwt, base + "/ws/v1/rooms/" + roomId, ttl);
    }
}
