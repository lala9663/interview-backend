# 인증과 권한

## 1. 세 가지 인증

| 구간 | 수단 | 비밀값 | 코드 |
| --- | --- | --- | --- |
| 프론트 → 백엔드 `/api/v1/**` | `Authorization: Bearer <access 토큰>` (JWT, HS256) | `JWT_SECRET` | `auth.JwtAuthenticationFilter`, `auth.JwtTokenService` |
| Agent ↔ 백엔드 `/internal/v1/**` | `X-Internal-Key` 헤더 | `INTERNAL_API_KEY` (양방향 같은 값) | `internal.InternalKeyFilter`, `agent.HttpAgentClient` |
| 프론트 → Agent WebSocket | 방 입장권(JWT, HS256, 5분) | `ROOM_TICKET_SECRET` (Agent 와 공유) | `room.RoomTicketService` |

인증 없이 열린 경로: `/api/v1/health`, `/api/v1/auth/**`. 그 밖의 경로는 모두 거부한다(`SecurityConfig`).

## 2. 토큰

| 토큰 | 모양 | 수명 | 저장 |
| --- | --- | --- | --- |
| access | JWT. 클레임 `sub`(사용자 id), `employeeNo`, `name`, `role`, `iat`, `exp` | 1800초 (`app.security.access-ttl-seconds`) | 서버에 저장하지 않는다 |
| refresh | 무작위 문자열(JWT 아님) | 14일 (`app.security.refresh-ttl-seconds`) | `TokenStore` (local 메모리, dev/prod Redis) |

- `POST /auth/refresh { refreshToken }` → 새 access + **새 refresh**. 쓴 refresh 토큰은 바로 폐기한다(rotation).
- `POST /auth/logout { refreshToken }` → refresh 토큰 폐기. access 토큰은 만료될 때까지 유효하다(최대 30분).
- 사용자의 역할이 바뀌어도 이미 발급된 access 토큰에는 만료 전까지 옛 역할이 남는다.

오류 응답:

| 상황 | HTTP | code |
| --- | --- | --- |
| 토큰 없음, 서명 불일치, 형식 오류 | 401 | `UNAUTHORIZED` |
| access 토큰 만료 | 401 | `TOKEN_EXPIRED` → 프론트가 refresh 를 부른다 |
| refresh 토큰이 없거나 만료·폐기됨 | 401 | `UNAUTHORIZED` → 다시 로그인 |
| 권한 없음(ADMIN 전용, 배정되지 않은 면접) | 403 | `FORBIDDEN` |

## 3. dev-login (local 전용)

`POST /api/v1/auth/dev-login { "employeeNo": "E1001" }` → 토큰 응답.

- 사번이 `IV_USER_ACCOUNT` 에 있으면 암호 없이 통과시킨다.
- `DevLoginController`, `DevLoginAuthProvider` 에 `@Profile("local")` 이 붙어 있어 **다른 프로파일에서는 빈이 만들어지지 않고 경로가 404** 다.
- 시드 사번은 [setup.md](setup.md) 의 표를 본다.

## 4. AD SSO 를 붙일 자리 (ad 프로파일, 미구현)

지금 있는 것:

- `auth.AuthProvider` — "누구인지 확인"하는 인터페이스.
- `auth.AdSsoAuthProvider` (`@Profile("ad")`) — `authenticate()` 가 501 `SSO_NOT_IMPLEMENTED` 를 던진다.
- `auth.SsoLoginController` (`@Profile("ad")`) — `GET /api/v1/auth/sso/login`. 지금은 위의 501 을 그대로 돌려준다.

붙이는 순서:

1. 사내 SSO 방식을 확인한다(SAML 2.0 / OIDC / Kerberos(SPNEGO) 중 무엇인지, 콜백 주소 등록 절차, 사번이 담기는 속성 이름).
2. 방식에 맞는 의존성을 `build.gradle` 에 추가한다(Java 8 · Spring Boot 2.7 호환 버전). 폐쇄망 미러에 있는지 먼저 확인한다.
3. `SsoLoginController.login()` 에서 SSO 로그인 화면으로 redirect 한다.
4. 콜백 엔드포인트를 `SsoLoginController` 에 추가한다(예: `GET /api/v1/auth/sso/callback`). `/api/v1/auth/**` 는 이미 인증 없이 열려 있다. **계약에 없는 경로이므로 계약 변경 절차를 밟는다.**
5. `AdSsoAuthProvider.authenticate()` 에서 SSO 응답을 검증하고 사번을 꺼내 `UserAccount` 를 찾는다.
6. `AuthService.issue(user)` 로 우리 토큰(access/refresh)을 만들어 프론트로 넘긴다. 넘기는 방법(redirect 의 fragment, 일회용 코드 교환 등)은 프론트와 정한다.
7. 그 뒤의 모든 API 인증은 지금과 같다(JWT). `SecurityConfig`, 필터, 컨트롤러는 고칠 필요가 없다.

정해야 할 것은 [open-questions.md](open-questions.md) 에 있다(처음 로그인한 사번을 자동 등록할지, 역할·담당 직무를 어디서 받을지).

## 5. 권한

- **ADMIN 전용**: 계약 표에 (ADMIN) 이 붙은 엔드포인트. 컨트롤러 메서드의 `@PreAuthorize("hasRole('ADMIN')")`.
  채용 등록, 직무 등록·수정, 업로드, 자동 매칭, 면접관 수동 지정, 질문 은행 등록·삭제.
- **면접 단위 쓰기**: 그 면접에 배정된 면접관(`IV_INTERVIEW_ASSIGNEE`)과 ADMIN 만. 서비스에서 `InterviewAccess.requireWritable(interviewId, user)`.
  방 생성·시작·종료·입장권, 꼬리질문 채택, 내 평가 저장, 질문 추가·수정, 브리핑 생성, 보고서 재생성, 면접 수정(PATCH),
  그 면접의 질문을 질문 은행에 담기(`POST /question-bank/from-question/{questionId}`).
- **조회**: 로그인한 사용자 모두. `GET /interviews/{id}/my-evaluation` 은 자기 것만 돌려준다.
- 컨트롤러에서 로그인 사용자는 `@AuthenticationPrincipal CurrentUser user` 로 받는다.

## 6. 비밀값 관리

- 비밀값은 환경변수로만 넣는다. 저장소에는 개발용 기본값만 있다.
- `auth.SecretsGuard` 가 시작할 때 검사한다: `JWT_SECRET`·`ROOM_TICKET_SECRET` 이 32바이트 미만이면 거부, `prod` 에서 `INTERNAL_API_KEY`·`ROOM_TICKET_SECRET` 이 개발용 기본값이면 거부.
- `JWT_SECRET` 을 바꾸면 발급된 access 토큰이 모두 무효가 된다(사용자는 refresh 로 다시 받는다).
- CORS 허용 출처는 `CORS_ALLOWED_ORIGINS`(쉼표로 여러 개). `/api/**` 에만 적용된다.
