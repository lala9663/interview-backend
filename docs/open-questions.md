# 가정으로 구현한 것과 바꿀 위치

현업·다른 팀의 답이 없어 가정으로 구현한 것들이다. 답이 오면 "바꿀 위치"를 고치고 이 표에서 지운다.

## 1. 현업에 물어야 하는 것

| # | 가정 | 바꿀 위치 |
| --- | --- | --- |
| 1 | 블라인드 항목은 성별, 생년월일, 가족사항, 사진, 주소, 출신지역, 나이. 값은 버리고 저장하지 않는다 | `excel-mapping.yml` 의 `blind: true` 항목 |
| 2 | 엑셀 컬럼은 수험번호, 성명, 지원직무코드, 지원직무명, 최종학력, 학교명, 전공, 학점, 주요수강과목, 자기소개서1~3, 프로젝트경험, 경력사항, 자격증, 어학, 수상경력. 첫 시트, 1행이 헤더 | `excel-mapping.yml` |
| 3 | 자소서 문항 제목은 지원동기 / 직무 역량과 경험 / 입사 후 포부 | `excel-mapping.yml` 의 `title` |
| 4 | 같은 파일 안의 수험번호 중복은 뒤의 행을 오류로 본다. 이미 있는 수험번호는 갱신한다 | `CandidateImportService` |
| 5 | 직무명은 엑셀의 "지원직무명"이 아니라 등록된 직무(JD)의 제목을 쓴다 | `CandidateImportService` |
| 6 | 자동 매칭이 만드는 면접: 신입 PT → 인성, 경력 포트폴리오 → 직무 → 인성. 면접관은 면접마다 2명 | `app.matching.*` (`application.yml`) |
| 7 | 매칭 기준은 면접관의 담당 직무 코드와 면접자의 직무 코드가 같은 것. 인성 면접도 같은 기준. 배정이 적은 사람부터 | `JdBasedInterviewerMatcher` (또는 새 `InterviewerMatcher`) |
| 8 | 신입은 대면 30분, 경력은 화상 60분이 기본 | `InterviewService.newInterview` |
| 9 | 평가 항목은 트랙·유형별 4개, 5점 만점. 등급(`grade`)은 자유 입력(20자) | `IV_EVAL_CRITERION` 데이터(시드: `LocalSeedRunner.seedCriteria`), `EvaluationService` |
| 10 | 면접은 배정된 면접관이 **모두** 평가를 제출하면 DONE. 보고서 완료 여부와는 무관 | `EvaluationService.completeIfAllSubmitted` |
| 11 | 제출한 평가도 다시 고칠 수 있다(잠그지 않는다) | `EvaluationService.saveMine` |
| 12 | 조회는 로그인한 사용자 모두에게 열려 있다(면접관이 배정되지 않은 면접자·보고서도 볼 수 있다) | `InterviewAccess` 에 조회용 검사를 추가하고 각 서비스의 조회에서 부른다 |
| 13 | 면접 등록(`POST /candidates/{id}/interviews`)은 ADMIN 만 가능(계약에 반영됨) | `InterviewController.create` 의 `@PreAuthorize` |
| 14 | 대시보드는 ADMIN 은 전체, 면접관은 자기 면접만 | `DashboardService` |
| 15 | 음성은 백엔드에 저장하지 않는다(전사만). 전사·보고서의 보관 기간과 삭제 절차는 없다 | 새 기능 |

## 2. 다른 레포·인프라와 맞춰야 하는 것

| # | 가정 | 바꿀 위치 |
| --- | --- | --- |
| 16 | AD SSO 방식(SAML / OIDC / Kerberos)을 모른다. 처음 로그인한 사번을 자동 등록할지, 역할·담당 직무를 어디서 받을지도 미정 | `AdSsoAuthProvider`, `SsoLoginController` ([auth.md](auth.md)) |
| 17 | dev/prod 에 사용자·평가 항목·채용 같은 기준 데이터를 넣는 수단이 없다(local 시드뿐). 채용·직무·질문 은행은 API 로 넣을 수 있지만 사용자와 평가 항목은 API 가 없다 | 관리 API(계약 변경) 또는 초기 데이터 SQL |
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

## 3. 계약에 모양이 없어 이 레포가 정한 것

처음 계약이 정하지 않아 이 레포가 정한 모양이다. **계약 2.10 "응답 모양 보충"에 반영됐다.** 바꿀 때는 계약 변경 절차를 따른다.

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
| UploadBatch 의 `errors[]` | 계약의 필드에 `code` 를 더했다. 이력 목록(`GET /recruitments/{id}/uploads`)에서는 `errors` 가 빈 배열이다 |
| 면접자 목록의 `status` 파라미터 | "지금 차수" 면접의 `InterviewStatus` |
| 면접자 목록의 `interviewers`, `briefingStatus` | "지금 차수" 면접의 것 |
| "지금 차수" | 끝나지 않은(DONE·CANCELED 아닌) 첫 면접. 모두 끝났으면 마지막으로 끝난 면접 |
| Interview 의 `reportStatus` | 가장 최근 보고서 작업의 상태 |
| 생성 응답의 HTTP 코드 | 계약이 202 로 정한 것 외에는 모두 200. 삭제는 204 |
| Agent 콜백의 응답 본문 | utterances·follow-ups `{ saved }`, refined-transcript `{ saved, jobId }`, events `{ ok }` |
| 추가 오류 코드 | `ROOM_NOT_LIVE`, `ROOM_ALREADY_ENDED`, `INTERVIEW_CANCELED`, `JOB_ALREADY_EXISTS`(직무 코드 중복), `SSO_NOT_IMPLEMENTED`(501), `METHOD_NOT_ALLOWED`(405), `INTERNAL_ERROR`(500), 업로드 오류 코드([excel-upload.md](excel-upload.md)) |

## 4. 계약에 반영된 것

- AI 작업 조회 경로는 `GET /ai-jobs/{jobId}` 다. JD 수정(`PUT /jobs/{id}`)과 `{id}` 의 뜻이 섞이지 않게 나눴다.
