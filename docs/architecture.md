# 구조

## 1. 역할

- 백엔드는 **기록의 주인**이다. DB 에 쓰는 것은 백엔드뿐이고, Agent 는 DB 에 닿지 않는다.
- Agent 는 **AI 만** 한다. 요약·질문·채점·보고서는 입력을 주면 결과를 돌려주는 호출이고, 실시간 방(STT·꼬리질문)만 Agent 가 상태를 가진다.
- 오래 걸리는 AI 호출은 백엔드가 **작업(AiJob)** 으로 관리하고, 프론트는 작업 상태를 조회한다.

## 2. 패키지

기능 단위 패키지다. 한 패키지에 그 기능의 controller / service / repository / entity / dto 가 모두 있다.

| 패키지 | 주요 클래스 | 계약 |
| --- | --- | --- |
| `common` | `ApiException`, `GlobalExceptionHandler`, `PageResponse`, `ListResponse`, `Times`, `Chunks`, `JsonNodeConverter`, `H2OracleModeDialect`, `HealthController` | 1, 2.9 |
| `auth` | `SecurityConfig`, `JwtTokenService`, `AuthService`, `TokenStore`, `AuthProvider`, `SecretsGuard` | 2.1 |
| `user` | `UserAccount`, `InterviewerController` | 2.4 |
| `recruitment` | `Recruitment`, `JobPosting`, `RecruitmentController` | 2.2 |
| `candidate` | `Candidate`, `CandidateProfile`, `CandidateService` / `imports.CandidateImportService`, `imports.CandidateSource` | 2.3 |
| `upload` | `ExcelCandidateSource`, `ExcelMappingLoader`, `UploadService`, `SampleExcelGenerator` | 2.3 |
| `matching` | `AutoAssignmentService`, `InterviewerMatcher`, `JdBasedInterviewerMatcher` | 2.4 |
| `interview` | `Interview`, `InterviewAssignee`, `InterviewService`, `InterviewAssembler`, `InterviewAccess`, `InterviewContext` | 2.5 |
| `briefing` | `Briefing`, `BriefingService`, `BriefingJobHandler` | 2.6 |
| `question` / `questionbank` | `Question`, `QuestionService` / `QuestionBankItem` | 2.6, 2.9 |
| `room` | `Room`, `Utterance`, `FollowUp`, `RoomEvent`, `RoomService`, `TranscriptService`, `RoomTicketService` | 2.7 |
| `internal` | `InternalRoomController`, `InternalRoomService`, `InternalKeyFilter` | 5 |
| `evaluation` | `EvalCriterion`, `InterviewerEvaluation`, `AiEvaluation`, `EvaluationService`, `AiEvaluationJobHandler` | 2.8 |
| `report` | `Report`, `ReportService`, `ReportJobHandler` | 2.8 |
| `job` | `AiJob`, `AiJobService`, `AiJobRunner`, `AiJobRecovery`, `AiJobHandler` | 2.8 |
| `agent` | `AgentClient`, `HttpAgentClient`, `FakeAgentClient`, `dto.AgentDtos` | 4 |
| `dashboard` | `DashboardService` | 2.9 |
| `adminstats` | `AdminStatsController`, `OperationsService`, `QualityService`, `InterviewerStatsService`, `OperationsProperties` | 2.11 |
| `seed` | `LocalSeedRunner` | - |

엔티티끼리는 JPA 연관(`@ManyToOne`)을 걸지 않고 id(`Long`)로만 가리킨다. 그래서 DB 에 FK 제약이 없다(정합성은 서비스 코드가 지킨다).

## 3. 교체 지점

| 무엇을 | 인터페이스 | 지금의 구현 | 바꾸는 법 |
| --- | --- | --- | --- |
| Agent 호출 | `agent.AgentClient` | `HttpAgentClient`(기본), `FakeAgentClient` | `app.agent.mode=http\|fake` (`AGENT_MODE`) |
| 면접자 입력 경로 | `candidate.imports.CandidateSource` | `upload.ExcelCandidateSource`, `candidate.imports.ApiCandidateSource`(변환만 있고 엔드포인트 없음) | 새 구현 + 컨트롤러. 저장은 모두 `CandidateImportService.importRows` |
| refresh 토큰 저장 | `auth.TokenStore` | `InMemoryTokenStore`, `RedisTokenStore` | `app.token-store.type=memory\|redis` |
| 로그인 방법 | `auth.AuthProvider` | `DevLoginAuthProvider`(local), `AdSsoAuthProvider`(ad, 미구현) | 프로파일. [auth.md](auth.md) |
| 면접관 매칭 | `matching.InterviewerMatcher` | `JdBasedInterviewerMatcher` | 새 구현 + `app.matching.strategy` |
| AI 작업 실행 | `job.AiJobHandler` | 브리핑, AI 평가, 보고서 | `JobType` 추가 + 새 구현 |
| 엑셀 컬럼·블라인드 항목 | `excel-mapping.yml` | - | 파일 수정. [excel-upload.md](excel-upload.md) |
| 트랙별 Agent 제한 시간 | `app.agent.new-grad.*`, `app.agent.experienced.*` | 신입 120초, 경력 보고서 3600초 | 설정값 |

## 4. 흐름

### 면접 전

```
POST /recruitments/{id}/uploads
  ExcelCandidateSource.read()            엑셀 → CandidateRow (블라인드 컬럼은 읽지 않음)
  CandidateImportService.importRows()    행 검증, 저장(같은 수험번호는 갱신)
  UploadService                          IV_UPLOAD_BATCH, 실패 행은 IV_UPLOAD_ERROR

POST /recruitments/{id}/assignments/auto
  AutoAssignmentService                  면접 없는 면접자에게 기본 면접 생성(app.matching.*-plan, 상태 WAITING)
                                         면접관 없는 면접에 InterviewerMatcher 로 배정
                                         (app.features.auto-briefing-on-match) 새 면접의 BRIEFING 작업 접수

BRIEFING 작업 (BriefingJobHandler)
  BriefingService.buildRequest()         profile(블라인드 제외) + JD + 질문 은행
  AgentClient.createBriefing()
  BriefingService.saveResult()           요약 저장, 질문(JD·RESUME·BANK) 교체
```

### 면접 중

```
POST /interviews/{id}/room        방 생성(이미 있으면 그 방), CREATED
POST /rooms/{id}/ticket           입장권 JWT(5분) + agentWsUrl
POST /rooms/{id}/start            CREATED → LIVE, 면접 IN_PROGRESS
(Agent) GET  /internal/v1/rooms/{id}/context          방을 열 때 한 번
(Agent) POST /internal/v1/rooms/{id}/utterances       확정 전사. (roomId, seq) 같으면 덮어씀
(Agent) POST /internal/v1/rooms/{id}/follow-ups       꼬리질문 후보
PUT /interviews/{id}/my-evaluation                    면접관 메모·점수 자동 저장
```

### 면접 후

```
POST /rooms/{id}/end              LIVE → ENDED, 면접 EVALUATING, AI_EVALUATION 작업 접수
  AiEvaluationJobHandler            payload { finalizeRoomId, reportBasis: "LIVE" }
    AgentClient.finalizeRoom()        실패해도 계속 진행(IV_ROOM_EVENT 에 FINALIZE_FAILED)
    AgentClient.evaluate()            → IV_AI_EVALUATION
    onFinished()                      성공·실패와 무관하게 REPORT 작업 접수 { basis: LIVE }
  ReportJobHandler
    AgentClient.createReport()        detail: 신입 BRIEF, 경력 DETAILED
    ReportService.saveNewVersion()    IV_REPORT 새 버전

(Agent) PUT /internal/v1/rooms/{id}/refined-transcript
  TranscriptService.replaceRefined()  전사 전체 교체, source=POST
  AI_EVALUATION 작업 접수 { reportBasis: REFINED }
    → 보정 전사로 AI 평가를 다시 하고(발화 번호가 바뀌므로) → REPORT { basis: REFINED } → 보고서 새 버전
  초안(LIVE) 작업이 보정본보다 늦게 끝나면 그 초안은 저장하지 않는다(ReportService.saveNewVersion)

PUT /interviews/{id}/my-evaluation { submitted: true }
  배정된 면접관이 모두 제출하면 면접 DONE
```

## 5. AI 작업(AiJob)

- `AiJobService.submit(type, interviewId, payload)` 가 `IV_AI_JOB` 에 PENDING 행을 만들고 이벤트를 낸다.
- `AiJobRunner` 가 **트랜잭션이 커밋된 뒤** 스레드풀(`aiJobExecutor`, `app.jobs.*`)에서 실행한다: PENDING → RUNNING → SUCCEEDED | FAILED.
- 실행기(`AiJobHandler`)는 Agent 호출을 트랜잭션 밖에서 하고, 결과 저장만 트랜잭션으로 묶는다.
- 실패하면 `errorCode`(`AGENT_UNAVAILABLE`, `MODEL_FAILED`, `JOB_FAILED`)와 `errorMessage` 가 남는다. 자동 재시도는 없다(화면에서 다시 요청).
- **재시작 복구**(`AiJobRecovery`): 서버가 뜰 때, 자기 `workerId`(`APP_INSTANCE_ID`, 기본 호스트 이름)로 남은 PENDING/RUNNING 작업을 `app.jobs.recovery` 에 따라 FAILED(`SERVER_RESTARTED`, 기본) 또는 재실행한다. 다른 서버의 작업은 건드리지 않는다.
- 상태를 보는 곳: `GET /ai-jobs/{jobId}`, `GET /interviews/{id}/jobs`, 그리고 Interview 의 `briefingStatus` / `reportStatus`.

## 6. 권한

| 대상 | 규칙 | 코드 |
| --- | --- | --- |
| `/api/v1/health`, `/api/v1/auth/**` | 인증 없음 | `SecurityConfig` |
| 그 밖의 `/api/v1/**` | access 토큰(JWT) | `JwtAuthenticationFilter` |
| 계약 표의 (ADMIN) | ADMIN 만 | 컨트롤러의 `@PreAuthorize("hasRole('ADMIN')")` |
| 방·평가·질문·브리핑 생성·보고서 재생성·면접 수정 | 그 면접에 배정된 면접관 또는 ADMIN | `InterviewAccess.requireWritable` |
| 조회(GET) | 로그인한 사용자 모두 | - |
| `/internal/v1/**` | `X-Internal-Key` | `InternalKeyFilter` |

## 7. 블라인드

1. **업로드**: `excel-mapping.yml` 의 `blind: true` 컬럼은 셀을 읽지 않는다. 매핑에 없는 컬럼도 읽지 않는다. 어떤 항목이 있었는지만 `blindedFields`(키)로 남긴다.
2. **저장**: `CandidateProfile` 에는 블라인드 항목을 담을 필드가 없다.
3. **Agent 요청**: `AgentDtos` 에 면접자 이름·수험번호 필드가 없다. profile 만 간다.
4. **글 속의 표현**(자소서에 가족 이야기를 쓴 경우 등)은 Agent 가 걸러 `excluded` 로 알려 준다. 백엔드는 그것을 `blindedFields` 와 합쳐 브리핑의 `excluded` 로 보여 준다.
5. `BlindFieldTest` 가 DB·응답·Agent 요청·방 context 에 블라인드 값이 없는지 검사한다.

## 8. 관리자 집계 (adminstats)

계약 2.11 의 세 엔드포인트(`/admin/operations`, `/admin/quality`, `/admin/interviewer-stats`). ADMIN 전용, 읽기 전용이고 테이블을 새로 만들지 않는다.

- **조회 방식**: 면접 수와 무관하게 조회 횟수가 일정하다. 건수(전사, 꼬리질문, 질문, 작업, 보고서 시각)는 각 기능 repository 의 JPQL 집계(`count`, `group by`, `min`/`max`)로 가져오고, 채용 범위는 서브쿼리(`interviewId in (select i.id from Interview i where i.recruitmentId = :rid)`)로 건다. 발화를 엔티티로 읽지 않는다.
- **자바에서 계산하는 것**: 점수는 JSON(CLOB)에 들어 있어 DB 에서 집계할 수 없다. AI 평가와 제출된 면접관 평가 행을 한 번씩 읽어 계산한다(`ScoreJson`, `StatMath`).
- **운영 현황판**: `OperationsService` 가 먼저 트랜잭션 밖에서 `AgentClient.stats()`(Agent 의 `GET /internal/v1/stats`, 제한 시간 `app.agent.stats-timeout-seconds`)를 부르고, 그 뒤 DB 를 읽어 방별로 합친다. Agent 호출이 실패하면 `agent.status: "DOWN"` 으로 답하고 나머지는 그대로 돌려준다(진행 중인 방에 `AGENT_DOWN`).
- **주의 기준**: `app.operations.*`(`OperationsProperties`).
- **마지막 전사 시각**: `IV_UTTERANCE.SAVED_AT`(백엔드가 그 줄을 저장한 시각). 값이 없는 옛 행은 방 시작 시각 + 마지막 발화의 `endMs` 로 어림한다.
