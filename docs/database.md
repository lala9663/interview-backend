# 데이터베이스

## 1. 테이블

모두 `IV_` 접두사. PK 는 `ID`(NUMBER(19)), 테이블마다 시퀀스 `<테이블>_SEQ`. FK 제약은 없다(id 로만 가리킨다).

| 테이블 | 엔티티 | 내용 | 유니크 |
| --- | --- | --- | --- |
| `IV_USER_ACCOUNT` | `user.UserAccount` | 사용자(면접관·관리자), 담당 직무 코드 | `EMPLOYEE_NO` |
| `IV_RECRUITMENT` | `recruitment.Recruitment` | 채용(공고), 트랙 | |
| `IV_JOB_POSTING` | `recruitment.JobPosting` | 채용별 직무와 JD(`JD_TEXT` CLOB) | `RECRUITMENT_ID, JOB_CODE` |
| `IV_CANDIDATE` | `candidate.Candidate` | 면접자. `PROFILE_JSON`(CLOB), `BLINDED_FIELDS` | `RECRUITMENT_ID, APPLICANT_NO` |
| `IV_UPLOAD_BATCH` | `upload.UploadBatch` | 업로드 이력(행 수) | |
| `IV_UPLOAD_ERROR` | `upload.UploadError` | 업로드 실패 행 | |
| `IV_INTERVIEW` | `interview.Interview` | 면접(유형, 차수, 방식, 상태, 일정) | |
| `IV_INTERVIEW_ASSIGNEE` | `interview.InterviewAssignee` | 면접관 배정(`MATCHED_BY`) | `INTERVIEW_ID, USER_ID` |
| `IV_BRIEFING` | `briefing.Briefing` | 사전 리포트. `SUMMARY_JSON`(CLOB) | `INTERVIEW_ID` |
| `IV_QUESTION` | `question.Question` | 면접별 질문 | |
| `IV_QUESTION_BANK` | `questionbank.QuestionBankItem` | 질문 은행 | |
| `IV_ROOM` | `room.Room` | 면접 방 | `INTERVIEW_ID` |
| `IV_UTTERANCE` | `room.Utterance` | 확정 전사(`RAW_TEXT`, `REFINED_TEXT` CLOB, `SOURCE_KIND` LIVE/POST) | `ROOM_ID, SEQ_NO` |
| `IV_FOLLOW_UP` | `room.FollowUp` | 꼬리질문 후보 | |
| `IV_ROOM_EVENT` | `room.RoomEvent` | 방 이벤트 기록 | |
| `IV_EVAL_CRITERION` | `evaluation.EvalCriterion` | 평가 항목(트랙·유형별 기준 데이터) | `TRACK, INTERVIEW_TYPE, CODE` |
| `IV_EVALUATION` | `evaluation.InterviewerEvaluation` | 면접관 평가(메모, `SCORES_JSON`) | `INTERVIEW_ID, USER_ID` |
| `IV_AI_EVALUATION` | `evaluation.AiEvaluation` | AI 참고 평가 | `INTERVIEW_ID` |
| `IV_REPORT` | `report.Report` | 보고서 버전(`CONTENT_JSON` CLOB) | `INTERVIEW_ID, VERSION_NO` |
| `IV_AI_JOB` | `job.AiJob` | AI 작업 상태 | |

정확한 컬럼은 `db/oracle/schema.sql` 을 본다(자동 생성본).

### 규칙

- 열거형은 이름 문자열(VARCHAR2(20))로 저장한다.
- JSON 모양 데이터는 CLOB 에 JSON 문자열로 저장한다(`JsonNodeConverter`, `CandidateProfileConverter`). DB 에서 JSON 내부를 조건으로 조회하지 않는다.
- 짧은 문자열 목록(직무 코드, 블라인드 키)은 쉼표로 이어 VARCHAR2 한 컬럼에 저장한다(`StringListConverter`).
- 시각은 업무 시간대(`app.time-zone`, 기본 Asia/Seoul)의 값으로 TIMESTAMP 에 저장한다(시간대 정보 없음).
- VARCHAR2 는 `(n CHAR)` 로 만들어지고 n 은 1000 이하다. 한글 1000자는 UTF-8 로 3000바이트라 Oracle 한도(4000바이트) 안에 든다.
- boolean 은 NUMBER(1,0).
- 기준 데이터(`IV_USER_ACCOUNT`, `IV_EVAL_CRITERION`, `IV_QUESTION_BANK`, 채용·직무)는 local 에서만 시드로 들어간다. dev/prod 에는 **사용자와 평가 항목을 넣는 수단이 아직 없다**([open-questions.md](open-questions.md)).

## 2. local: H2 (Oracle 호환 모드)

```
jdbc:h2:file:./data/interview;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;AUTO_SERVER=TRUE
```

- `MODE=Oracle`: Oracle 문법·동작에 가깝게 한다. `DEFAULT_NULL_ORDERING=HIGH`: NULL 정렬을 Oracle 처럼(오름차순에서 맨 뒤).
- 방언은 `com.interview.common.H2OracleModeDialect`: H2 방언에서 페이지 문법만 `offset ? rows fetch next ? rows only` 로 바꾼 것이다(Oracle 호환 모드의 H2 가 `limit ?` 를 받지 않는다).
- H2 는 Oracle 이 아니다. 호환 모드로도 못 잡는 것: 빈 문자열 = NULL, VARCHAR2 4000바이트 한도, 식별자 30자 한도(12.1 이하), `IN` 1000개 한도. 이것들은 AGENTS.md 의 규칙과 `OracleSchemaTest` 로 막는다.

## 3. Oracle 로 전환 (dev / prod)

1. **스키마 만들기**: `db/oracle/schema.sql` 을 대상 스키마에서 실행한다(시퀀스 → 테이블 → 인덱스·유니크).
2. **환경변수**: `SPRING_PROFILES_ACTIVE=dev`(또는 `prod`), `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
3. 앱은 `ddl-auto=validate` 로 뜬다. 테이블·컬럼이 엔티티와 다르면 시작하지 못한다(오류 메시지에 어긋난 컬럼이 나온다).
4. 드라이버는 `ojdbc8`(Spring Boot 가 버전을 관리), 방언은 `Oracle12cDialect`(12c 이상의 `fetch first` 페이지 문법을 쓴다. 11g 면 `application-dev.yml` 의 `database-platform` 을 `Oracle10gDialect` 로 바꾼다).

이 PC 에 Oracle 이 없어 실제 Oracle 에서의 실행(스키마 생성, validate, 쿼리)은 **확인하지 못했다**. 반입 후 가장 먼저 확인할 것이다.

### schema.sql 을 다시 만드는 법

```bash
./gradlew generateOracleSchema
```

- `src/test/java/com/interview/tools/OracleSchemaGenerator` 가 엔티티를 스캔해 Hibernate 의 스키마 생성 결과를 Oracle 방언으로 출력한다. DB 연결이 필요 없다.
- 엔티티를 바꾸고 다시 만들지 않으면 `OracleSchemaTest.schemaFileIsUpToDate` 가 실패한다.

### 이미 운영 중인 DB 를 바꿀 때

`schema.sql` 은 "처음부터 만드는" 스크립트다. 운영 DB 에는 통째로 실행하지 않는다.

1. 엔티티를 고치고 `./gradlew generateOracleSchema`.
2. `git diff db/oracle/schema.sql` 로 바뀐 부분을 본다.
3. 그 차이를 `ALTER TABLE ...` 문으로 옮겨 `db/oracle/changes/날짜_설명.sql` 로 저장한다(예: `20261120_add_interview_location.sql`).
4. 배포 순서: ALTER 실행 → 앱 배포. 컬럼 삭제·이름 변경은 두 번에 나눈다(새 컬럼 추가 → 앱 배포 → 옛 컬럼 삭제).

Flyway 같은 마이그레이션 도구는 아직 넣지 않았다([open-questions.md](open-questions.md)).

## 4. Redis Sentinel

백엔드는 Redis 를 **refresh 토큰 저장**에만 쓴다(`auth.RedisTokenStore`, 키 `iv:refresh:<토큰>`, 값 사용자 id, TTL 14일). 방의 실시간 상태는 Agent 가 따로 Redis 에 둔다.

`application-dev.yml` / `application-prod.yml`:

```yaml
spring:
  redis:
    password: ${REDIS_PASSWORD:}
    timeout: 3s
    sentinel:
      master: ${REDIS_SENTINEL_MASTER}      # Sentinel 이 감시하는 마스터 이름
      nodes: ${REDIS_SENTINEL_NODES}        # host:port 를 쉼표로
      password: ${REDIS_SENTINEL_PASSWORD:} # Sentinel 자체에 암호가 있을 때
app:
  token-store:
    type: redis
```

- 클라이언트는 Lettuce(Spring Boot 기본). 연결은 처음 쓸 때 맺으므로 Redis 가 죽어 있어도 앱은 뜨지만, 로그인·토큰 갱신이 실패한다.
- Sentinel 이 아닌 단일 Redis 로 붙으려면 `sentinel` 블록 대신 `spring.redis.host`, `spring.redis.port` 를 쓴다.
- local 은 `app.token-store.type=memory` 라 Redis 를 쓰지 않는다(서버를 다시 띄우면 다시 로그인).
- 이 PC 에 Redis 가 없어 `RedisTokenStore` 는 **실행해 확인하지 못했다**.
