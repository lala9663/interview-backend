package com.interview.auth;

import com.interview.user.UserDto;

import lombok.Getter;

/** API 명세 2.1 의 토큰 응답. */
@Getter
public class TokenResponse {

    private final String accessToken;
    private final String refreshToken;
    private final long expiresIn;
    private final UserDto user;

    public TokenResponse(String accessToken, String refreshToken, long expiresIn, UserDto user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.user = user;
    }
}
