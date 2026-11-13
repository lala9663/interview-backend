package com.interview.auth;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.interview.common.ApiException;
import com.interview.user.UserAccount;

/**
 * AD SSO 자리 (ad 프로파일). 아직 구현되지 않았다.
 *
 * 붙이는 방법 (docs/auth.md 에 자세히):
 * 1. 사내 SSO 방식(SAML / OIDC / Kerberos)을 확인하고 필요한 의존성을 build.gradle 에 추가한다.
 * 2. GET /api/v1/auth/sso/login ({@link SsoLoginController}) 에서 SSO 로그인 화면으로 보낸다.
 * 3. SSO 가 돌려보내는 콜백 엔드포인트를 SsoLoginController 에 추가하고, 여기 authenticate() 에서
 *    응답(assertion / id token)을 검증해 사번을 꺼낸다.
 * 4. 사번으로 IV_USER_ACCOUNT 를 찾는다(없으면 만들지 거부할지는 현업 결정 - docs/open-questions.md).
 * 5. AuthService.issue(user) 로 우리 토큰(access/refresh)을 발급해 프론트로 넘긴다.
 *    이후의 모든 API 인증은 지금과 같다(JWT).
 */
@Component
@Profile("ad")
public class AdSsoAuthProvider implements AuthProvider {

    @Override
    public String name() {
        return "ad-sso";
    }

    @Override
    public UserAccount authenticate(Map<String, String> credentials) {
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "SSO_NOT_IMPLEMENTED", "AD SSO 연동은 아직 구현되지 않았습니다.");
    }
}
