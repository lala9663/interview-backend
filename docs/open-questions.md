# 가정으로 구현한 것과 바꿀 위치

현업·다른 팀의 답이 없어 가정으로 구현한 것들이다. 답이 오면 "바꿀 위치"를 고치고 이 표에서 지운다.

## 1. 현업에 물어야 하는 것

| # | 가정 | 바꿀 위치 |
| --- | --- | --- |
| 1 | 블라인드 항목은 성별, 생년월일, 가족사항, 사진, 주소, 출신지역, 나이. 값은 버리고 저장하지 않는다 | `excel-mapping.yml` 의 `blind: true` 항목 |
| 2 | 엑셀 컬럼은 수험번호, 성명, 지원직무코드, 지원직무명, 지원유형(선택), 최종학력, 학교명, 전공, 학점, 주요수강과목, 자기소개서1~3, 프로젝트경험, 경력사항, 자격증, 어학, 수상경력. 첫 시트, 1행이 헤더 | `excel-mapping.yml` |
| 3 | 자소서 문항 제목은 지원동기 / 직무 역량과 경험 / 입사 후 포부 | `excel-mapping.yml` 의 `title` |
| 4 | 같은 파일 안의 수험번호 중복은 뒤의 행을 오류로 본다. 이미 있는 수험번호는 갱신한다 | `CandidateImportService` |
| 5 | 직무명은 엑셀의 "지원직무명"이 아니라 등록된 직무(JD)의 제목을 쓴다 | `CandidateImportService` |
| 6 | 자동 매칭이 만드는 면접: 신입도 경력도 직무 → 인성(2026-11-18 확인). 면접관은 면접마다 2명 | `app.matching.*` (`application.yml`) |
| 7 | 매칭 기준은 면접관의 담당 직무 코드와 면접자의 직무 코드가 같은 것. 인성 면접도 같은 기준. 배정이 적은 사람부터 | `JdBasedInterviewerMatcher` (또는 새 `InterviewerMatcher`) |
| 8 | 신입은 대면 30분, 경력은 화상 60분이 기본 | `InterviewService.newInterview` |
| 9 | 평가 항목은 트랙·유형별 4개, 5점 만점. 시드는 신입 직무(문제 해결, 논리성, 직무 지식, 전달력), 경력 직무(직무 지식, 문제 해결, 전문성, 협업), 인성(트랙 공통). 등급(`grade`)은 자유 입력(20자) | `IV_EVAL_CRITERION` 데이터(시드: `LocalSeedRunner.seedCriteria`), `EvaluationService` |
| 10 | 면접은 배정된 면접관이 **모두** 평가를 제출하면 DONE. 보고서 완료 여부와는 무관 | `EvaluationService.completeIfAllSubmitted` |
| 11 | 제출한 평가도 다시 고칠 수 있다(잠그지 않는다) | `EvaluationService.saveMine` |
| 12 | 조회는 로그인한 사용자 모두에게 열려 있다(면접관이 배정되지 않은 면접자·보고서도 볼 수 있다) | `InterviewAccess` 에 조회용 검사를 추가하고 각 서비스의 조회에서 부른다 |
| 13 | 면접 등록(`POST /candidates/{id}/interviews`)은 ADMIN 만 가능(API 명세에 반영됨) | `InterviewController.create` 의 `@PreAuthorize` |
| 14 | 대시보드는 ADMIN 은 전체, 면접관은 자기 면접만 | `DashboardService` |
| 15 | 음성은 백엔드에 저장하지 않는다(전사만). 전사·보고서의 보관 기간과 삭제 절차는 없다 | 새 기능 |
| 42 | 실시간 기능 사용 여부(`liveEnabled`)는 트랙으로만 정한다(신입 끔, 경력 켬). 면접 방식(대면·화상)이나 면접 한 건 단위로는 나누지 않는다 | `app.live.*` (`LiveProperties`) |
| 43 | 면접자 유형은 트랙과 맞는지 검사하지 않는다(신입 채용에 "일반 경력"이 들어와도 그대로 저장). 엑셀의 모르는 값은 오류 없이 비워 두고, 유형 열이 없는 파일을 다시 올려도 이미 넣은 유형은 지우지 않는다 | `ApplicantType.parse`, `CandidateImportService` |
| 44 | AI 참고 평가 권한(`app.evaluation.ai-visible-to`)은 `GET /interviews/{id}/ai-evaluation` 에만 건다. 보고서 본문과 관리자 집계는 그대로다 | `AiEvaluationService.get` |
| 45 | 페르소나: 이름만 필수다(설명·질문 유형·꼬리질문 유형은 비워도 된다). 이름 중복은 앞뒤 공백을 뗀 글자가 같은 것. `recommended: true` 와 `enabled: false` 를 함께 보내면 400. 면접이 고른 페르소나를 지우면 그 면접은 기본 선택을 따른다 | `PersonaService` |
| 46 | 페르소나를 고르지 않은 면접은 방이 LIVE·ENDED 여도 그때그때의 기본 선택을 보여 준다(시작할 때의 기본 선택을 기록해 두지 않는다) | `PersonaService.Resolver`, `InterviewPersonas` |
| 47 | 면접의 페르소나를 바꿔도 적용되는 페르소나가 그대로면(기본 선택과 같은 것을 고름) 사전 리포트를 다시 만들지 않는다. 사전 리포트를 만드는 작업이 돌고 있으면 새 작업을 접수하지 않고, 그 작업이 성공으로 끝난 뒤 시작할 때의 페르소나와 지금의 페르소나가 다르면 한 번 더 만든다(실패로 끝나면 다시 만들지 않는다) | `InterviewService.setPersona`, `BriefingService.regenerateIfExists`, `BriefingService.regenerateIfPersonaChanged` |
| 48 | `previousRoundChecks` 의 "바로 앞 차수"는 차수(seq)가 정확히 하나 앞인 면접이다. 그 면접이 취소됐으면 더 앞의 차수를 찾지 않고 null | `ReportService.previousRoundChecks` |
| 49 | 질문 피드백의 `votes` 에 같은 질문이 두 번 오거나 빈(null) 항목이 오면 400. 의견을 줄 수 있는 질문의 종류는 가리지 않는다(그 면접의 질문이면 된다). 사전 리포트를 다시 만들어 사라진 질문의 의견은 조회에서 빼고 돌려준다(저장된 값은 다음 저장 때까지 그대로다) | `QuestionFeedbackService` |
| 53 | 실시간 기능을 쓰지 않는 면접(`liveEnabled: false`)의 운영 현황판 주의는 시작 지연과 시간 초과뿐이다. 가장 최근 AI 평가·보고서 작업이 실패한 것(`JOB_FAILED`)도 붙이지 않는다(그 면접은 그런 작업을 만들지 않으므로). `silentMinutes` 는 0 으로 둔다. 대시보드의 보고서 대기에도 넣지 않는다 | `OperationsService.toItem`, `DashboardService` |
| 54 | 실시간 기능을 쓰지 않는 면접의 `POST /interviews/{id}/report/regenerate` 는 `409 LIVE_DISABLED`. DB 제약(유니크 등)에 걸린 저장은 `409 CONFLICT` 로 답한다(페르소나 이름은 `PERSONA_NAME_TAKEN`). `CONFLICT` 는 아직 API 명세에 없는 코드다 | `ReportService.regenerate`, `GlobalExceptionHandler` |

## 2. 다른 레포·인프라와 맞춰야 하는 것

| # | 가정 | 바꿀 위치 |
| --- | --- | --- |
| 16 | AD SSO 방식(SAML / OIDC / Kerberos)을 모른다. 처음 로그인한 사번을 자동 등록할지, 역할·담당 직무를 어디서 받을지도 미정 | `AdSsoAuthProvider`, `SsoLoginController` ([auth.md](auth.md)) |
| 17 | dev/prod 에 사용자·평가 항목·채용 같은 기준 데이터를 넣는 수단이 없다(local 시드뿐). 채용·직무·질문 은행은 API 로 넣을 수 있지만 사용자와 평가 항목은 API 가 없다 | 관리 API(API 변경) 또는 초기 데이터 SQL |
| 18 | Oracle 은 12c 이상(`fetch first` 문법). 실제 Oracle 에서 실행해 보지 못했다 | `application-dev.yml` 의 `database-platform` ([database.md](database.md)) |
| 19 | Redis 는 Sentinel 구성, refresh 토큰 저장에만 쓴다. 실제 Redis 에서 실행해 보지 못했다 | `RedisTokenStore`, `application-dev.yml` |
| 20 | 스키마 변경은 `schema.sql` 의 차이를 손으로 ALTER 문으로 옮긴다. Flyway 등은 쓰지 않는다 | [database.md](database.md) 3장 |
| 21 | 서버 재시작 때 끝나지 않은 AI 작업은 FAILED 로 바꾼다(재실행하지 않는다). 서버 이름은 호스트 이름 | `app.jobs.recovery`, `APP_INSTANCE_ID` |
| 22 | AI 작업은 실패해도 자동으로 다시 하지 않는다. 화면에서 다시 요청한다 | `AiJobRunner` |
| 23 | 방 종료 때 Agent `finalize` 가 실패해도 이미 저장된 전사로 AI 평가·보고서를 진행한다 | `AiEvaluationJobHandler` |
| 24 | `finalize` 가 응답하면 남은 음성의 전사가 이미 백엔드에 저장됐다고 본다(바로 AI 평가를 시작한다) | `AiEvaluationJobHandler` (필요하면 대기 추가) |
| 25 | 사후 STT 보정본이 오면 전사를 통째로 바꾸고(실시간 전사는 남기지 않는다) AI 평가와 보고서를 보정 전사로 다시 만든다(발화 번호가 바뀌어 근거 번호가 어긋나기 때문). 초안과 보정본 작업이 겹치면 늦게 끝난 초안 보고서는 저장하지 않는다 | `TranscriptService.replaceRefined`, `InternalRoomService.refinedTranscript` |
| 26 | 보정본이 온 뒤에 늦게 도착한 실시간 전사는 버린다 | `TranscriptService.saveLive` |
| 27 | 경력 면접자를 API 로 받는 경로는 변환(`ApiCandidateSource`)만 있고 엔드포인트가 없다 | `.opencode/skills/add-candidate-source` |
| 50 | 브리핑 요청의 `applicantType`·`persona`·`predictedFollowUps`, 방 context 의 `persona`, `/health` 의 `llmStatus`·`sttStatus` 는 API 명세에 "Agent 에 아직 반영하지 않았다"고 적혀 있다. 백엔드는 보내고 읽을 준비만 돼 있고, Agent 가 `questions[].followUps` 를 주기 전에는 예상 꼬리질문이 저장되지 않는다(`FakeAgentClient` 는 준다) | `agent/dto/AgentDtos`, `HttpAgentClient.health` |
| 51 | 시스템 상태의 `waits` 는 성공한(SUCCEEDED) 작업만, 접수(`createdAt`)부터 끝(`finishedAt`)까지의 시간으로 센다(대기열에서 기다린 시간 포함). `DB` 는 집계 쿼리가 되면 OK, 예외면 DOWN 이고 그때 `waits`·`queue`·`dailySessions` 는 빈 목록이다. 응답은 5초 동안 기억한다(`checkedAt` 이 실제로 확인한 시각). Agent 의 `/health` 결과도 `HttpAgentClient` 가 5초 동안 기억한다 | `SystemStatusService`, `HttpAgentClient.health` |
| 52 | 시스템 상태의 `detail` 글자는 DB "면접 데이터 저장소", STT "면접 중의 말을 글로 옮김"으로 뒀다(프런트엔드 모의 데이터의 "Oracle", "Whisper" 는 실제로 쓰는 제품을 백엔드가 알 수 없어 쓰지 않았다) | `SystemStatusService` |

## 3. API 명세에 모양이 없어 이 레포가 정한 것

처음 API 명세가 정하지 않아 이 레포가 정한 모양이다. **API 명세 2.10 "응답 모양 보충"에 반영됐다.** 바꿀 때는 API 변경 절차를 따른다. 다만 업로드·공통 오류 코드 가운데 `FILE_EMPTY`, `HEADER_MISSING`, `FILE_TOO_MANY_ROWS`, `FILE_TOO_LARGE`, `METHOD_NOT_ALLOWED`, `INTERNAL_ERROR`는 아직 API 명세에 없고 이 문서와 `excel-upload.md`에만 있다.

| 항목 | 정한 모양 |
| --- | --- |
| `GET /dashboard/summary` 응답 | `{ date, todayCount, liveRoomCount, pendingEvaluationCount, pendingReportCount, todayInterviews[], liveRooms[], pendingEvaluations[], pendingReports[] }`. 목록 항목은 Interview. 목록은 각각 최대 100건 |
| `POST /recruitments/{id}/assignments/auto` 응답 | `{ candidateCount, createdInterviewCount, assignedInterviewCount, unassignedInterviewCount, briefingJobCount, unassigned[{ interviewId, candidateId, candidateName, jobCode, type }] }` |
| `GET /candidates/{id}/progress` 응답 | `{ candidateId, currentStage{type,status}, items[{ interviewId, seq, type, mode, status, scheduledAt, reportStatus, current }] }` |
| `GET /rooms/{id}` 응답 | `{ id, interviewId, status, createdAt, startedAt, endedAt, lastSeq, refined }` |
| `GET /rooms/{id}/follow-ups` 항목 | `{ id, clientId, basedOnSeq, text, rationale, used, questionId, createdAt }` |
| `GET /interviews/{id}/criteria` 항목 | `{ code, name, description, maxScore }` |
| `GET /interviews/{id}/my-evaluation` 응답 | `{ interviewId, memo, scores[{criterionCode,score,comment}], grade, submitted, updatedAt, submittedAt }`. 없으면 빈 값 |
| `PUT /interviews/{id}/my-evaluation` | null 인 필드는 바꾸지 않는다(일부만 보내도 된다) |
| `PATCH /interviews/{id}` 본문 | `{ scheduledAt?, durationMinutes?, mode?, status? }`. WAITING 인 면접에 일정을 넣으면 SCHEDULED 가 된다 |
| `GET /interviews/{id}/report` (보고서 없음) | 모든 필드 null, `status` 만 가장 최근 보고서 작업의 상태(없으면 null) |
| `GET /interviews/{id}/ai-evaluation` (없음) | `status` 는 가장 최근 작업의 상태(없으면 null), `scores` 는 빈 배열 |
| `GET /interviews/{id}/report/versions` | 최신 버전이 먼저. 항목은 Report 전체(content 포함) |
| `GET /uploads/template` | 가짜 50행 샘플. `?empty=true` 면 헤더만 |
| UploadBatch 의 `errors[]` | API 명세의 필드에 `code` 를 더했다. 이력 목록(`GET /recruitments/{id}/uploads`)에서는 `errors` 가 빈 배열이다 |
| 면접자 목록의 `status`, `type` 파라미터 | "지금 차수" 면접의 `InterviewStatus`, `InterviewType` |
| 면접자 목록의 `interviewers`, `briefingStatus`, `currentStage` | "지금 차수" 면접의 것 |
| "지금 차수" | 끝나지 않은(DONE·CANCELED 아닌) 첫 면접. 모두 끝났으면 마지막으로 완료된(DONE) 면접(취소된 면접은 세지 않고, 모두 취소면 없음) |
| Interview 의 `reportStatus` | 가장 최근 보고서 작업의 상태 |
| 생성 응답의 HTTP 코드 | API 명세가 202 로 정한 것 외에는 모두 200. 삭제는 204 |
| Agent 콜백의 응답 본문 | utterances·follow-ups `{ saved }`, refined-transcript `{ saved, jobId }`, events `{ ok }` |
| 추가 오류 코드 | `ROOM_NOT_LIVE`, `ROOM_ALREADY_ENDED`, `INTERVIEW_CANCELED`, `JOB_ALREADY_EXISTS`(직무 코드 중복), `SSO_NOT_IMPLEMENTED`(501), `METHOD_NOT_ALLOWED`(405), `INTERNAL_ERROR`(500), 업로드 오류 코드([excel-upload.md](excel-upload.md)) |

## 4. API 명세에 반영된 것

- AI 작업 조회 경로는 `GET /ai-jobs/{jobId}` 다. JD 수정(`PUT /jobs/{id}`)과 `{id}` 의 뜻이 섞이지 않게 나눴다.

## 5. 관리자 집계(API 명세 2.11)에서 가정한 것

| # | 가정 | 바꿀 위치 |
| --- | --- | --- |
| 28 | 주의 기준: 시작 지연 5분, 시간 초과 5분, 무전사 3분, STT 대기 5 이상 또는 건너뜀 1 이상 | `app.operations.*` |
| 29 | Agent 가 여러 대면 `stats` 는 백엔드가 물어본 한 대의 것이다. 다른 대가 맡은 방은 `audioConnected: null` 이고 `NO_AUDIO`·`STT_BACKLOG` 가 붙지 않는다. 진행 중인데 Agent 가 모르는 방(아무도 접속하지 않은 방)도 같은 이유로 `NO_AUDIO` 를 붙이지 않는다 | `OperationsService` (Agent 쪽에서 전체 방을 모아 주는 API 가 생기면 교체) |
| 30 | `elapsedMinutes` 는 시작 전이면 null. `lateMinutes`·`overtimeMinutes`·`silentMinutes`·`queueDepth`·`droppedChunks` 는 해당 없으면 0. Agent 가 DOWN 이면 `agent.rooms` 는 null, `providers` 는 빈 배열 | `OperationsService` |
| 31 | 현황판의 `summary.live` 는 면접 상태 IN_PROGRESS 의 수. 취소된 면접은 `total` 에만 든다 | `OperationsService` |
| 32 | `endedInterviewCount` 는 방이 ENDED 인 면접 수 | `QualityService` |
| 33 | "준비된 질문"은 브리핑이 만든 질문(JD·RESUME·BANK) 가운데 숨기지 않은 것. `autoMatched` 는 물은 질문 가운데 전사의 `matchedQuestionId` 로 걸린 것. `bySource` 는 같은 집계를 출처별로 나눈 것 | `QualityService`, `QuestionRepository` |
| 34 | `followUps.roomCount` 는 꼬리질문 제안이 한 건이라도 있었던 방의 수 | `FollowUpRepository.summarizeOfRecruitment` |
| 35 | `scoreGap.byCriterion` 은 항목 코드로 묶는다(트랙·유형이 달라도 코드가 같으면 한 줄). 정렬은 코드 순 | `QualityService` |
| 36 | 보고서 소요 시간은 방 종료 → 그 면접의 첫 LIVE / 첫 REFINED 보고서. 다시 만든 보고서는 세지 않는다 | `ReportRepository.firstGeneratedOfRecruitment` |
| 37 | 면접관 편차의 대상은 배정이 있거나 제출한 평가가 있는 사용자. `assignedCount` 는 취소된 면접을 뺀다. `pendingCount` 는 EVALUATING·DONE 인 배정 가운데 미제출 | `InterviewerStatsService` |
| 38 | `deviationFromPeers` 의 "다른 면접관들의 평균"은 면접관별 그 면접 평균의 평균. `scoreDistribution` 의 키는 1~최대 점수(반올림한 정수) | `InterviewerStatsService` |
| 39 | 비율도 소수 둘째 자리로 반올림한다(API 명세의 예시와 같다). 아주 작은 비율은 0.0 으로 보일 수 있다 | `StatMath.rate` |
| 40 | 집계 성능: 채용 한 건에 면접 1,000건·발화 수만 건을 가정했다. 호출마다 다시 계산하고 캐시는 없다. 점수 비교는 평가 행(면접 수 × 면접관 수)을 메모리로 읽는다. 실제 Oracle 에서의 실행 계획은 확인하지 못했다(`IV_UTTERANCE.MATCHED_QUESTION_ID` 에는 인덱스가 없다) | 느리면 짧은 캐시 또는 집계 테이블 |
| 41 | 시드의 진행 중 면접 두 건(경력 채용)은 서버를 처음 띄운 시각 기준으로 만들어진다. 시간이 지나면 초과·무전사 시간이 계속 늘어난다 | `LocalSeedRunner` (`data/` 를 지우면 다시 만들어진다) |
