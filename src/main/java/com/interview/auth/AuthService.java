package com.interview.auth;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.interview.common.ApiException;
import com.interview.user.UserAccount;
import com.interview.user.UserAccountRepository;
import com.interview.user.UserDto;

import lombok.RequiredArgsConstructor;

/** 토큰 발급·갱신·폐기. 사용자를 확인하는 일은 {@link AuthProvider} 가 한다. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenService jwtTokenService;
    private final TokenStore tokenStore;
    private final AppSecurityProperties props;
    private final UserAccountRepository userRepository;

    public TokenResponse issue(UserAccount user) {
        String refreshToken = UUID.randomUUID().toString() + UUID.randomUUID().toString().replace("-", "");
        tokenStore.save(refreshToken, user.getId(), props.getRefreshTtlSeconds());
        return new TokenResponse(jwtTokenService.createAccessToken(user), refreshToken, props.getAccessTtlSeconds(),
                UserDto.from(user));
    }

    /** refresh 토큰은 한 번 쓰면 버리고 새로 준다(rotation). */
    public TokenResponse refresh(String refreshToken) {
        Long userId = tokenStore.findUserId(refreshToken);
        UserAccount user = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (user == null || !user.isActive()) {
            throw ApiException.unauthorized("UNAUTHORIZED", "refresh 토큰이 유효하지 않습니다. 다시 로그인해 주세요.");
        }
        tokenStore.remove(refreshToken);
        return issue(user);
    }

    public void logout(String refreshToken) {
        tokenStore.remove(refreshToken);
    }
}
