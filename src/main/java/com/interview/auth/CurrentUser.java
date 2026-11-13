package com.interview.auth;

import com.interview.user.UserRole;

import lombok.Getter;

/** access 토큰에서 꺼낸 로그인 사용자. 컨트롤러에서 @AuthenticationPrincipal CurrentUser 로 받는다. */
@Getter
public class CurrentUser {

    private final Long id;
    private final String employeeNo;
    private final String name;
    private final UserRole role;

    public CurrentUser(Long id, String employeeNo, String name, UserRole role) {
        this.id = id;
        this.employeeNo = employeeNo;
        this.name = name;
        this.role = role;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
