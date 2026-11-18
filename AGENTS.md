# AGENTS.md — interview-backend

면접 지원 서비스의 백엔드(Spring). 이 파일은 AI 코딩 도구가 작업 전에 읽는 규칙이다. 사람에게도 같은 규칙이다.
답변·주석·문서·오류 메시지는 **한국어**, 코드 식별자는 영어로 쓴다.

## 1. 스택 (바꾸지 않는다)

Java 8 · Spring Boot 2.7.5 · Gradle 7.5.1(wrapper) · Spring Data JPA(Hibernate 5.6) · Oracle(운영) / H2 Oracle 호환 모드(local·test)
· Redis Sentinel · JWT(jjwt 0.11.5) · Apache POI 5.2.3 · Lombok · JUnit 5

## 2. 금지 사항

- **Java 8 문법만.** `var`, `record`, text block(`"""`), switch 식, `List.of`/`Map.of`, `String.isBlank()`, `Optional.isEmpty()`, `stream().toList()` 금지. `javax.*` 를 쓴다(`jakarta.*` 아님).
- **네이티브 SQL 금지.** `@Query(nativeQuery = true)`, `JdbcTemplate`, `createNativeQuery` 를 쓰지 않는다. JPQL, 파생 쿼리, `Specification` 만.
- **Oracle 에서 깨지는 것 금지** (이름 규칙과 VARCHAR 길이는 `OracleSchemaTest` 가 검사한다):
  - 테이블 이름은 `IV_` 로 시작. 테이블·컬럼·인덱스·시퀀스 이름은 30자 이내.
  - 예약어를 이름으로 쓰지 않는다: USER, COMMENT, SIZE, LEVEL, DATE, NUMBER, ROW, MODE, TYPE, FILE, ORDER, VALUE ... (`INTERVIEW_TYPE`, `ROW_NO`, `SOURCE_KIND` 처럼 바꾼다)
  - PK 는 `@SequenceGenerator(allocationSize = 1)`. `IDENTITY` 금지.
  - `VARCHAR` 길이는 1000 이하(한글 3바이트 × 1000 < Oracle 한도 4000바이트). 더 길면 `@Lob`.
  - 빈 문자열은 Oracle 에서 NULL 이다. 자유 입력 문자열 컬럼에 `nullable = false` 를 걸지 않는다. `'' = ''` 같은 비교에 기대지 않는다.
  - `IN (...)` 에 1000개 넘게 넣지 않는다 → `Chunks.of(ids)` 로 나눈다.
- **엔티티 사이에 `@ManyToOne` 등 연관을 걸지 않는다.** 다른 엔티티는 `Long xxxId` 로만 가리킨다(기능 패키지 간 결합과 지연 로딩 문제를 피한다).
- JSON 모양 데이터(profile, summary, content, scores)는 `@Lob @Convert(converter = JsonNodeConverter.class)` 로 CLOB 에 저장한다.
- **블라인드 항목(성별, 생년월일, 가족, 주소 등)을 저장·응답·Agent 요청에 넣지 않는다.** Agent 로 가는 DTO(`agent/dto/AgentDtos`)와 방 context 에 면접자 이름·수험번호를 넣지 않는다. 바꿨으면 `BlindFieldTest` 를 돌린다.
- API 명세(`docs/contract/api-contract.md`)의 경로·필드명·열거형을 임의로 바꾸지 않는다(6장 절차).
- 공용 파일에 기능별 내용을 쌓지 않는다: 거대한 enum 모음, 오류 코드 모음, 공용 설정 클래스를 만들지 않는다.
- 실제 사람의 정보, 사내 주소·비밀키를 코드·테스트·문서에 넣지 않는다. 시드와 샘플은 가짜 데이터만.
- git commit/push 는 사람이 요청했을 때만 한다.

## 3. 패키지 규칙

`com.interview.<feature>` 한 패키지 안에 그 기능의 controller / service / repository / entity / dto 를 **모두** 둔다(계층별 패키지 없음).

| 패키지 | 내용 |
| --- | --- |
| `common` | ApiException, 오류·목록 응답, `Times`(시각), `Chunks`, JSON 변환기. **여기에 기능 코드를 넣지 않는다** |
| `auth` `user` | JWT·보안 설정·TokenStore·AuthProvider / 사용자 |
| `recruitment` `candidate` `upload` `matching` `interview` | 면접 전 |
| `persona` | 면접 페르소나(목록·관리, 기본 다섯 가지 넣기, 면접에 적용되는 페르소나 고르는 규칙) |
| `briefing` `question` `questionbank` | 사전 리포트와 질문(예상 꼬리질문, 질문 피드백 포함) |
| `room` `internal` | 면접 중(방·전사·꼬리질문) / Agent 콜백 |
| `evaluation` `report` `job` | 면접 후(평가·보고서) / AI 작업 |
| `agent` | Agent 클라이언트(`AgentClient`: HTTP, Fake) |
| `dashboard` `seed` | 집계 / local 시드 |
| `adminstats` | 관리자 집계(운영 현황판, AI 품질, 면접관 편차). 읽기 전용. 다른 기능의 repository 집계 쿼리를 읽어 조립한다 |
| `systemstatus` | 시스템 상태(서비스 연결, AI 작업 대기, 일별 면접 방 수). 읽기 전용. `job`·`room` 의 집계 쿼리와 `AgentClient.health()` 를 읽어 조립한다 |

- 다른 기능의 데이터가 필요하면 그 기능의 **service** 를 부른다. 읽기 전용 조립(`InterviewAssembler`)만 repository 를 직접 쓴다.
- enum, 오류 코드, 설정(`@ConfigurationProperties`)은 **주인 기능의 패키지**에 둔다. 예: `InterviewStatus` → `interview`, `app.matching.*` → `matching/MatchingProperties`, `app.live.*` → `interview/LiveProperties`, `app.evaluation.*` → `evaluation/EvaluationProperties`.
- 오류는 `ApiException.notFound("X_NOT_FOUND", "한국어 메시지")` 처럼 코드 문자열을 바로 쓴다. 응답 모양은 `{ code, message, details }` 로 고정.
- 시각: DB 는 `LocalDateTime`(`Times.now()`), 응답은 `OffsetDateTime`(`Times.toOffset`), 요청은 `Times.toLocal`.
- 목록 응답은 `ListResponse`(`{items}`) 또는 `PageResponse`(`{items,page,size,total}`).

## 4. 교체 지점 (인터페이스 + 설정)

| 인터페이스 | 구현 | 고르는 설정 |
| --- | --- | --- |
| `agent.AgentClient` | `HttpAgentClient`, `FakeAgentClient` | `app.agent.mode=http\|fake` |
| `candidate.imports.CandidateSource` | `upload.ExcelCandidateSource`, `ApiCandidateSource`(자리) | 호출하는 컨트롤러 |
| `auth.TokenStore` | `InMemoryTokenStore`, `RedisTokenStore` | `app.token-store.type=memory\|redis` |
| `auth.AuthProvider` | `DevLoginAuthProvider`(local), `AdSsoAuthProvider`(ad, 자리) | 프로파일 |
| `matching.InterviewerMatcher` | `JdBasedInterviewerMatcher` | `app.matching.strategy=jd` |
| `job.AiJobHandler` | 브리핑·AI 평가·보고서 실행기 | `JobType` |

새 구현을 붙일 때는 기존 구현을 고치지 말고 **새 클래스 + 설정값**으로 한다.

## 5. 기능을 추가하는 순서

1. API 명세를 확인한다. API 명세에 없는 API 면 먼저 6장 절차.
2. 주인 패키지를 정한다(없으면 새 패키지). 엔티티 → repository → service → dto → controller 순으로 만든다.
3. 엔티티를 만들거나 바꿨으면 `./gradlew generateOracleSchema` 로 `db/oracle/schema.sql` 을 다시 만든다.
4. 권한을 건다: ADMIN 전용은 컨트롤러에 `@PreAuthorize("hasRole('ADMIN')")`, 면접 단위 쓰기는 서비스 첫 줄에 `interviewAccess.requireWritable(interviewId, user)`.
5. 오래 걸리는 Agent 호출은 요청 스레드에서 하지 않는다. `AiJobService.submit(...)` + `AiJobHandler` 로 만든다. Agent 호출은 트랜잭션 밖, 저장만 `@Transactional`.
6. 테스트를 쓴다(`ApiTestSupport` 를 상속하면 로그인·호출·대기 도우미가 있다). local 에서 화면이 보이게 하려면 `seed/LocalSeedRunner` 에 가짜 데이터를 더한다.
7. 문서를 고친다(`docs/` 의 해당 파일, 필요하면 이 파일).
8. `./gradlew build` 가 통과하는지 확인한다.

자세한 절차는 `.opencode/skills/` 에 있다: `add-api-endpoint`, `add-entity-and-migration`, `change-api-contract`, `add-candidate-source`, `port-legacy-feature`.

## 6. API 변경 절차

- 원본은 세 레포 공통 문서(`docs/03-api-contract.md`)이고, 이 레포의 `docs/contract/api-contract.md` 는 복사본이다.
- API 명세를 바꿔야 하면: **코드부터 고치지 않는다.** 변경안(무엇을, 왜, 어느 레포가 영향받는지)을 적어 세 레포 담당자가 합의 → 원본 수정 → 세 레포의 복사본을 같은 날 갱신 → 그다음 코드.
- 필드 **추가**는 하위 호환이다(먼저 백엔드가 내보내고 프론트가 나중에 쓴다). 이름 변경·삭제·의미 변경은 호환이 깨지므로 새 필드를 추가하고 옛 필드를 한동안 함께 둔다.
- API 명세에 모양이 안 적힌 응답(예: 대시보드, 자동 매칭 결과)은 `docs/open-questions.md` 에 적어 둔 모양을 따른다.

## 7. 명령어

```bash
./gradlew build                    # 컴파일 + 테스트 (끝내기 전에 반드시)
./gradlew test                     # 테스트만
./gradlew test --tests '*CoreFlow*'
./gradlew bootRun                  # local, 8080. Agent 없이 AI 작업까지: AGENT_MODE=fake ./gradlew bootRun
./gradlew generateOracleSchema     # db/oracle/schema.sql 다시 만들기
```

- Windows 는 `gradlew.bat`. JDK 는 8 이어야 한다(`java -version`).
- 테스트 프로파일은 `local,test`: 메모리 H2 + Fake Agent + 시드. 테스트 클래스들이 DB 를 공유하므로 **다른 테스트가 만든 데이터에 기대지 말고**, 필요한 데이터는 그 테스트 안에서 만든다.

## 8. 브랜치 규칙 (3명이 동시에 작업, `dev` + `main`)

- 길게 사는 브랜치는 둘이다. `dev`는 작업을 모으는 곳이고, `main`은 매주 화요일에 올리는 확인된 지점이다.
- 작업 브랜치는 `dev`에서 따서 하루 이틀 안에 PR로 `dev`에 합친다(스쿼시 병합: 작업 하나가 커밋 하나로 남는다). 오래 사는 작업 브랜치를 두지 않는다.
- `main`에는 직접 커밋하지 않는다. 화요일에 병합 담당이 세 서비스를 함께 띄워 확인한 뒤 `dev`를 `main`으로 올리고(일반 병합) 태그를 붙인다(예: `2026-w47`). 세 레포는 같은 날 같은 태그로 올린다.
- `dev`는 항상 빌드되고 테스트가 통과해야 한다. 깨졌으면 새 작업보다 복구가 먼저다.
- 작업 브랜치 이름은 `feat/<짧은-설명>`, `fix/<짧은-설명>`.
- PR은 작게 올린다(리뷰하는 사람이 15분 안에 읽을 크기, 한 가지 목적). 대략 300줄 이내다. PR 에 `./gradlew build` 결과를 붙인다.
- 커밋 메시지(= PR 제목)는 `<영역>: <무엇을 왜>` 한 줄, 한국어. 예: `speaker: 되묻기 구간 규칙 추가`. 영역은 기능 폴더나 패키지 이름이다. API 명세를 바꾸면 앞에 `[API 변경]`을 붙인다. `dev`에는 스쿼시로 합치므로 PR 제목이 그대로 커밋 한 줄이 된다.
- 리뷰는 1명, 영역이 다른 사람이 본다. API 명세를 바꾸는 PR은 세 명 모두 확인한다.
- 덜 끝난 기능은 **기능 플래그** 뒤에 두고 합친다: `application.yml` 의 `app.features.<이름>: false` → 코드에서 `@Value("${app.features.<이름>:false}")` 또는 `@ConditionalOnProperty`. 끝나면 플래그를 지운다.
- **공용 파일 수정을 최소화한다.** 충돌이 잦은 파일: `application.yml`, `build.gradle`, `SecurityConfig`, `LocalSeedRunner`, `excel-mapping.yml`, `db/oracle/schema.sql`, `AGENTS.md`.
  - 이 파일들은 다른 변경과 섞지 말고 **작은 PR 로 먼저** 합친다.
  - `application.yml` 은 자기 기능의 `app.<feature>:` 블록만 건드린다.
  - `schema.sql` 은 손으로 고치지 않는다. 충돌이 나면 양쪽 엔티티를 합친 뒤 `./gradlew generateOracleSchema` 로 다시 만든다.
- 작업 중에 범위 밖의 아이디어나 개선점이 나오면 그 자리에서 만들지 않고 세 레포 공통 문서의 `09-backlog.md`(할 목록)에 적는다. 적는 법은 그 문서 맨 위에 있다.
- 다른 사람의 패키지를 고쳐야 하면 PR 설명에 적고 그 담당자에게 알린다. 리팩터링과 기능 변경을 한 PR 에 섞지 않는다.
- 합치기 전에 `./gradlew build`. 실패한 채로 합치지 않는다.

## 9. 다른 두 레포와의 관계

| 레포 | 역할 | 이 레포와의 접점 |
| --- | --- | --- |
| `interview-frontend` (Vue) | 화면 | `/api/v1/**` (JWT). CORS 허용 출처는 `app.security.cors-allowed-origins` |
| `interview-agent` (Python) | STT, 꼬리질문, 요약, 채점, 보고서 | 백엔드 → Agent: `agent/HttpAgentClient`. Agent → 백엔드: `internal/InternalRoomController`. 방 입장권 비밀키 공유 |

- **백엔드가 기록의 주인이다.** Agent 는 DB 에 닿지 않는다. Agent 가 저장할 것은 내부 API 로 백엔드에 보낸다.
- **AI 로직은 여기에 두지 않는다.** 프롬프트, 모델 호출, 유사도 계산, 텍스트 분석은 Agent 몫이다. 여기에는 저장·권한·업무 규칙만 둔다.
- Agent 없이 개발할 때는 `FakeAgentClient` 를 쓴다. API 명세 4장의 요청·응답 모양이 바뀌면 `AgentDtos`, `FakeAgentClient`, `HttpAgentClientTest` 를 함께 고친다.

## 10. 문서 위치

| 문서 | 내용 |
| --- | --- |
| `README.md` | 개요, 빠른 시작, 환경변수 |
| `docs/contract/api-contract.md` | API 명세(복사본) |
| `docs/architecture.md` | 패키지, 흐름, 교체 지점 |
| `docs/setup.md` | 설치·실행, 폐쇄망 설정 |
| `docs/database.md` | 테이블, Oracle 전환, Redis Sentinel |
| `docs/auth.md` | JWT, dev-login, AD SSO 를 붙일 자리 |
| `docs/excel-upload.md` | 엑셀 매핑, 오류 코드 |
| `docs/open-questions.md` | 가정으로 구현한 것과 바꿀 위치 |
| `.opencode/skills/*/SKILL.md` | 작업별 절차 |

문서는 사실만 간결하게 쓰고, 코드가 바뀌면 같은 PR 에서 고친다.
