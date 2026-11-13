package com.interview.auth;

import java.util.Map;

import com.interview.user.UserAccount;

/**
 * "누구인지 확인"하는 방법의 교체 지점. 확인이 끝나면 {@link AuthService#issue(UserAccount)} 가 토큰을 만든다.
 * - {@link DevLoginAuthProvider}: local 프로파일. 사번만으로 로그인.
 * - {@link AdSsoAuthProvider}: ad 프로파일. 자리만 있다 (docs/auth.md).
 */
public interface AuthProvider {

    String name();

    /** 확인에 실패하면 ApiException(401)을 던진다. */
    UserAccount authenticate(Map<String, String> credentials);
}
