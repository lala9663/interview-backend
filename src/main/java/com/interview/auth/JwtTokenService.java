package com.interview.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.interview.user.UserAccount;
import com.interview.user.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

/** access 토큰(JWT, HS256) 발급과 검증. */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final AppSecurityProperties props;

    public String createAccessToken(UserAccount user) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("employeeNo", user.getEmployeeNo())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + props.getAccessTtlSeconds() * 1000L))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** 만료면 ExpiredJwtException, 그 밖의 문제는 JwtException 을 던진다. */
    public CurrentUser parseAccessToken(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody();
        return new CurrentUser(Long.valueOf(claims.getSubject()), claims.get("employeeNo", String.class),
                claims.get("name", String.class), UserRole.valueOf(claims.get("role", String.class)));
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(props.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }
}
