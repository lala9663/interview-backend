package com.interview.auth;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** POST /api/v1/auth/dev-login — local 프로파일에서만 존재한다(다른 프로파일에서는 404). */
@RestController
@RequestMapping("/api/v1/auth")
@Profile("local")
@RequiredArgsConstructor
public class DevLoginController {

    private final DevLoginAuthProvider authProvider;
    private final AuthService authService;

    @PostMapping("/dev-login")
    public TokenResponse devLogin(@RequestBody Map<String, String> body) {
        return authService.issue(authProvider.authenticate(body));
    }
}
