package com.interview.auth;

import java.util.Collections;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interview.common.ApiException;
import com.interview.user.UserDto;
import com.interview.user.UserService;

import lombok.RequiredArgsConstructor;

/** POST /auth/refresh, POST /auth/logout, GET /me */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/auth/refresh")
    public TokenResponse refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw ApiException.validation("refreshToken 이 필요합니다.");
        }
        return authService.refresh(refreshToken);
    }

    @PostMapping("/auth/logout")
    public Map<String, Object> logout(@RequestBody(required = false) Map<String, String> body) {
        if (body != null) {
            authService.logout(body.get("refreshToken"));
        }
        return Collections.<String, Object>singletonMap("ok", true);
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal CurrentUser user) {
        return UserDto.from(userService.get(user.getId()));
    }
}
