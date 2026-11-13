package com.interview.auth;

import java.util.Collections;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * GET /api/v1/auth/sso/login — ad 프로파일. 자리만 있다.
 * 구현할 때: 여기서 SSO 로그인 화면으로 redirect 하고, 콜백 엔드포인트를 추가한다 ({@link AdSsoAuthProvider} 주석).
 */
@RestController
@RequestMapping("/api/v1/auth/sso")
@Profile("ad")
@RequiredArgsConstructor
public class SsoLoginController {

    private final AdSsoAuthProvider authProvider;
    private final AuthService authService;

    @GetMapping("/login")
    public TokenResponse login() {
        // 지금은 501(SSO_NOT_IMPLEMENTED)을 돌려준다.
        return authService.issue(authProvider.authenticate(Collections.<String, String>emptyMap()));
    }
}
