package com.interview.auth;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.interview.common.ApiException;
import com.interview.user.UserAccount;
import com.interview.user.UserAccountRepository;

import lombok.RequiredArgsConstructor;

/** local 전용: 사번이 DB 에 있으면 통과시킨다. 다른 프로파일에서는 빈이 만들어지지 않는다. */
@Component
@Profile("local")
@RequiredArgsConstructor
public class DevLoginAuthProvider implements AuthProvider {

    private final UserAccountRepository userRepository;

    @Override
    public String name() {
        return "dev-login";
    }

    @Override
    public UserAccount authenticate(Map<String, String> credentials) {
        String employeeNo = credentials.get("employeeNo");
        UserAccount user = employeeNo == null ? null : userRepository.findByEmployeeNo(employeeNo.trim()).orElse(null);
        if (user == null || !user.isActive()) {
            throw ApiException.unauthorized("UNAUTHORIZED", "등록되지 않은 사번입니다.");
        }
        return user;
    }
}
