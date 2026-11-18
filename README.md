# interview-backend

면접 지원 웹 애플리케이션의 백엔드. 면접자·면접·평가·보고서의 **기록을 맡는다**(DB 에 쓰는 것은 이 서비스뿐이다).
AI(요약, 질문, STT, 채점, 보고서)는 별도 서비스인 Agent 가 하고, 백엔드는 Agent 를 부르고 결과를 저장한다.

```
브라우저(Vue) ──REST /api/v1 (JWT)──▶ 백엔드(이 레포) ── JPA ──▶ Oracle (local 은 H2)
     │                                   │  ▲
     │                     X-Internal-Key│  │ 콜백 /internal/v1
     └──WebSocket (입장권)──────────────▶ Agent(Python) ──▶ 사내 LLM / Whisper
```

- 스택: Java 8, Spring Boot 2.7.5, Gradle 7.5.1, Spring Data JPA, Oracle, Redis(Sentinel), JWT(jjwt), Apache POI
- API 명세: [docs/contract/api-contract.md](docs/contract/api-contract.md) (세 레포 공통 문서의 복사본)
- AI 코딩 도구(OpenCode)용 규칙: [AGENTS.md](AGENTS.md), 작업 절차: `.opencode/skills/`

## 빠른 시작 (local)

JDK 8 만 있으면 된다. Oracle·Redis·Agent 없이 뜬다.

```bash
./gradlew bootRun          # local 프로파일, 포트 8080
```

```bash
curl http://localhost:8080/api/v1/health
# {"status":"UP","agent":"DOWN"}   ← Agent 가 없으면 DOWN. 백엔드는 정상이다.

curl -X POST http://localhost:8080/api/v1/auth/dev-login \
     -H "Content-Type: application/json" -d '{"employeeNo":"E1001"}'
# accessToken 을 받아 Authorization: Bearer <토큰> 으로 쓴다.

curl -H "Authorization: Bearer <토큰>" http://localhost:8080/api/v1/recruitments
```

- 처음 뜰 때 가짜 시드 데이터가 들어간다: 사용자 6명(`E1001` 김서연 ADMIN, `E1002`~`E1006` 면접관), 채용 2개, 면접자 24명(신입 12, 경력 12), 오늘 날짜의 면접들.
- DB 파일은 `./data/` 에 생긴다. 지우면 시드부터 다시 시작한다.
- Agent 없이 AI 작업(브리핑·평가·보고서)까지 보려면 가짜 Agent 로 띄운다: `AGENT_MODE=fake ./gradlew bootRun`
- 샘플 엑셀: 로그인 후 `GET /api/v1/uploads/template` (가짜 50행, 오류 행 3개 포함). 그대로 신입 공채에 올리면 성공 47·오류 3 이 나온다.

## 명령어

| 명령 | 하는 일 |
| --- | --- |
| `./gradlew build` | 컴파일 + 테스트 + jar |
| `./gradlew test` | 테스트만 (메모리 H2 + Fake Agent) |
| `./gradlew bootRun` | local 로 실행 |
| `./gradlew generateOracleSchema` | 엔티티로부터 `db/oracle/schema.sql` 을 다시 만든다 |

## 프로파일

| 프로파일 | DB | 토큰 저장소 | 로그인 | 비고 |
| --- | --- | --- | --- | --- |
| `local` | H2 (Oracle 호환 모드, 파일 `./data/`), `ddl-auto=update` | 메모리 | dev-login | 시드 데이터. `bootRun` 의 기본값 |
| `dev` | Oracle, `ddl-auto=validate` | Redis Sentinel | (ad 와 함께) | 개발 서버 |
| `prod` | Oracle, `ddl-auto=validate` | Redis Sentinel | (ad 와 함께) | 개발용 기본 비밀키면 시작을 거부한다 |
| `ad` | - | - | AD SSO **자리만 있음** | `prod,ad` 처럼 함께 켠다. [docs/auth.md](docs/auth.md) |

jar 로 실행할 때는 프로파일을 반드시 지정한다: `SPRING_PROFILES_ACTIVE=prod,ad java -jar build/libs/interview-backend-0.1.0.jar`

## 환경변수

| 이름 | 쓰는 프로파일 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 전부 | (bootRun 은 `local`) | 프로파일 |
| `SERVER_PORT` | 전부 | `8080` | 포트 |
| `JWT_SECRET` | 전부 | local 만 개발용 값 | access 토큰 서명 키(HS256, 32바이트 이상) |
| `INTERNAL_API_KEY` | 전부 | `dev-internal-key` | 백엔드 ↔ Agent 의 `X-Internal-Key` (양방향 같은 값) |
| `ROOM_TICKET_SECRET` | 전부 | `dev-room-ticket-secret-change-me-32bytes!!` | 방 입장권 서명 키. Agent 와 같은 값 |
| `CORS_ALLOWED_ORIGINS` | 전부 | `http://localhost:5173` | 쉼표로 여러 개. prod 는 필수 |
| `AGENT_MODE` | 전부 | `http` | `http` 또는 `fake` |
| `AGENT_BASE_URL` | 전부 | `http://localhost:8000` | 백엔드 → Agent REST 주소. prod 는 필수 |
| `AGENT_WS_BASE_URL` | 전부 | `ws://localhost:8000` | 프론트가 붙을 Agent WebSocket 주소(입장권 응답에 실린다). prod 는 필수 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | dev, prod | 없음 | Oracle 접속 |
| `DB_POOL_SIZE` | dev, prod | 20 / 30 | 커넥션 풀 크기 |
| `REDIS_SENTINEL_MASTER`, `REDIS_SENTINEL_NODES` | dev, prod | 없음 | 예: `mymaster`, `h1:26379,h2:26379,h3:26379` |
| `REDIS_PASSWORD`, `REDIS_SENTINEL_PASSWORD` | dev, prod | 빈 값 | Redis / Sentinel 암호 |
| `APP_INSTANCE_ID` | 전부 | 호스트 이름 | 서버 이름. AI 작업의 재시작 복구에 쓴다 |
| `EXCEL_MAPPING_LOCATION` | 전부 | `classpath:excel-mapping.yml` | 엑셀 컬럼 매핑 파일 위치 |

그 밖의 설정(제한 시간, 스레드풀, 매칭 인원 등)은 `src/main/resources/application.yml` 의 `app.*` 에 있다. 업무 규칙을 바꾸는 설정:

| 설정 | 기본값 | 뜻 |
| --- | --- | --- |
| `app.live.new-grad-enabled` | `false` | 신입 면접에서 실시간 기능(녹음, 전사, 꼬리질문 제안, 면접 뒤 AI 평가와 보고서)을 쓰는가. Interview 응답의 `liveEnabled` |
| `app.live.experienced-enabled` | `true` | 경력 면접에서 실시간 기능을 쓰는가 |
| `app.evaluation.ai-visible-to` | `ADMIN` | AI 참고 평가(`GET /interviews/{id}/ai-evaluation`)를 볼 수 있는 사람. `ADMIN` 이면 면접관은 403, `ALL` 이면 로그인한 사용자 모두 |
| `app.matching.new-grad-plan`, `app.matching.experienced-plan` | `[JOB, PERSONALITY]` | 자동 매칭이 면접 없는 면접자에게 만들어 주는 면접(1차, 2차 순) |

## 폴더 구조

```
src/main/java/com/interview/
  common/         오류 응답, 페이지 응답, 시각 변환, JSON 컬럼 변환기, health
  auth/           JWT, 보안 설정, TokenStore(메모리/Redis), AuthProvider(dev-login/AD SSO 자리)
  user/           사용자, 면접관 목록
  recruitment/    채용(공고), 직무(JD)
  candidate/      면접자 목록·상세·진행 / imports: 입력 경로 공통 저장 로직(CandidateSource)
  upload/         엑셀 업로드(매핑, 파서, 이력, 오류 행, 샘플 생성)
  matching/       면접관 자동 매칭(InterviewerMatcher)
  interview/      면접, 면접관 배정, 면접 단위 권한(InterviewAccess), 실시간 기능 사용 여부(LiveProperties), 면접에 페르소나 고르기
  persona/        면접 페르소나(목록, 관리자 관리, 기본 다섯 가지 넣기)
  briefing/       사전 리포트(브리핑) + BRIEFING 작업
  question/       면접별 질문(예상 꼬리질문 포함), 질문 피드백
  questionbank/   질문 은행
  room/           방, 입장권, 전사, 꼬리질문
  evaluation/     평가 항목, 면접관 평가, AI 참고 평가 + AI_EVALUATION 작업
  report/         보고서(버전) + REPORT 작업
  job/            AI 작업(AiJob) 접수·실행·재시작 복구
  agent/          Agent 클라이언트(HTTP / Fake)
  internal/       Agent 콜백(/internal/v1), X-Internal-Key 필터
  dashboard/      대시보드 집계
  adminstats/     관리자 집계(운영 현황, AI 품질, 면접관 편차). 읽기 전용
  systemstatus/   시스템 상태(서비스 연결, AI 작업 대기, 일별 면접 방 수). 읽기 전용
  seed/           local 시드 데이터
src/main/resources/
  application*.yml      프로파일별 설정
  excel-mapping.yml     엑셀 컬럼 매핑(블라인드 항목 포함)
src/test/java/com/interview/
  CoreFlowIntegrationTest   핵심 흐름 전체
  BlindFieldTest            블라인드 항목 누출 검사
  SecurityAndUploadErrorTest, agent/HttpAgentClientTest, tools/OracleSchemaTest
  adminstats/AdminStatsTest  관리자 집계 세 가지
  LiveDisabledTest          실시간 기능 잠금(입장권 409, 종료 뒤 작업 없음, 예상 꼬리질문), AI 참고 평가 권한
  BriefingAndFeedbackTest   사전 리포트의 previousRoundChecks, 질문 피드백
  candidate/ApplicantTypeTest, persona/PersonaApiTest, systemstatus/SystemStatusTest
  support/ApiTestSupport     테스트 공용 도우미(로그인, 호출, 대기)
  support/FixtureTestSupport 테스트용 채용·면접자·면접 만들기
  tools/OracleSchemaGenerator  Oracle DDL 생성기
db/oracle/schema.sql    Oracle DDL (자동 생성)
docs/                   설계·설정 문서, API 명세 복사본
.opencode/skills/       OpenCode 작업 절차
```

## 문서

- [docs/architecture.md](docs/architecture.md) — 패키지, 흐름, 교체 지점
- [docs/setup.md](docs/setup.md) — 설치와 실행, 폐쇄망
- [docs/database.md](docs/database.md) — 테이블, Oracle 전환, Redis Sentinel
- [docs/auth.md](docs/auth.md) — JWT, dev-login, AD SSO 를 붙일 자리
- [docs/excel-upload.md](docs/excel-upload.md) — 매핑 파일 고치는 법, 오류 코드
- [docs/open-questions.md](docs/open-questions.md) — 가정으로 구현한 것과 바꿀 위치
