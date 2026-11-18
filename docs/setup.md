# 설치와 실행

## 1. 필요한 것

| 무엇 | 버전 | 비고 |
| --- | --- | --- |
| JDK | 8 | `java -version` 이 1.8 이어야 한다. `JAVA_HOME` 설정 |
| Gradle | 7.5.1 | 설치하지 않는다. `./gradlew`(wrapper)가 받는다 |
| Oracle, Redis | - | local 에서는 필요 없다 |
| Agent | - | 없어도 뜬다(`health` 의 `agent` 가 `DOWN`, AI 작업은 `AGENT_UNAVAILABLE` 로 실패) |

## 2. local 실행

```bash
./gradlew bootRun                    # Windows: gradlew.bat bootRun
```

- 포트 8080, 프로파일 `local`.
- DB: `./data/interview.mv.db` (H2 파일, Oracle 호환 모드). 스키마는 자동 생성(`ddl-auto=update`).
- 빈 DB 면 시드 데이터가 들어간다. 다시 넣으려면 서버를 끄고 `data/` 폴더를 지운다.
- 시드는 기본 설정에 맞춰져 있다. 신입 채용(실시간 기능을 쓰지 않음)에는 사전 리포트, 준비된 질문과 예상 꼬리질문, 끝난 방, 면접관 평가만 있다. 진행 중인 방, 전사, 꼬리질문 후보, AI 평가, 보고서, AI 작업 기록은 경력 채용에 있다. 운영 현황·AI 품질·면접관 편차 화면은 경력 채용을 골라야 볼거리가 있다.
- 면접 유형에서 `PT`·`PORTFOLIO` 를 없애기 전에 만든 `data/` 는 그 값이 든 면접 행 때문에 읽을 때 오류가 난다. 서버를 끄고 `data/` 를 지운 뒤 다시 띄운다.
- 시드 면접의 날짜는 서버를 띄운 날로 옮겨진다(`app.seed.shift-dates-to-today`). 그래서 며칠 뒤에 띄워도 "오늘 면접"이 보인다.
- Agent 없이 AI 작업까지 보려면: `AGENT_MODE=fake ./gradlew bootRun` (PowerShell: `$env:AGENT_MODE='fake'; .\gradlew.bat bootRun`)

로그인(dev-login)에 쓰는 시드 사번:

| 사번 | 이름 | 역할 | 담당 직무 |
| --- | --- | --- | --- |
| E1001 | 김서연 | ADMIN | SW |
| E1002 | 박준호 | INTERVIEWER | SW, AI |
| E1003 | 이하린 | INTERVIEWER | HW |
| E1004 | 최민재 | INTERVIEWER | AI, SW |
| E1005 | 정유진 | INTERVIEWER | MGMT |
| E1006 | 한지후 | INTERVIEWER | SW, HW, MGMT |

확인:

```bash
curl http://localhost:8080/api/v1/health
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/dev-login \
  -H "Content-Type: application/json" -d '{"employeeNo":"E1001"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/recruitments"
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/recruitments/1/candidates?size=5"
curl -H "Authorization: Bearer $TOKEN" -o sample.xlsx "http://localhost:8080/api/v1/uploads/template"
curl -H "Authorization: Bearer $TOKEN" -F "file=@sample.xlsx" "http://localhost:8080/api/v1/recruitments/1/uploads"
```

샘플 엑셀의 직무 코드는 신입 공채용이다(`upload.SampleExcelGenerator`). 신입은 기본 설정(`app.live.new-grad-enabled=false`)에서 실시간 기능이 꺼져 있어, 올린 면접자로는 질문 화면과 평가 입력까지만 된다(입장권은 `409 LIVE_DISABLED`, 종료해도 AI 작업을 접수하지 않는다). 전사·보고서까지 보려면 경력 채용의 시드 면접으로 들어간다.

Windows 의 Git Bash 에서 `curl -d '{"한글":...}'` 처럼 한글을 명령줄에 직접 쓰면 인코딩이 깨져 400 이 날 수 있다. 본문을 UTF-8 파일로 만들어 `--data-binary @파일` 로 보낸다.

## 3. 세 서비스를 함께 띄울 때

| 서비스 | 주소 | 맞춰야 하는 값 |
| --- | --- | --- |
| 프론트(Vite) | `http://localhost:5173` | 백엔드의 `CORS_ALLOWED_ORIGINS` |
| 백엔드 | `http://localhost:8080` | - |
| Agent | `http://localhost:8000` | `INTERNAL_API_KEY`, `ROOM_TICKET_SECRET` 이 백엔드와 같아야 한다 |

비밀키(`INTERNAL_API_KEY`, `ROOM_TICKET_SECRET`)와 주소는 기본값끼리 이미 맞춰져 있다. 하나는 바꿔야 한다: Agent 의 `BACKEND_MODE` 는 기본값이 `fake`(가짜 백엔드)라, `BACKEND_MODE=http` 로 띄워야 Agent 가 이 백엔드에서 방 정보를 읽고 전사·꼬리질문을 저장한다(agent 레포 `docs/setup.md` 3장).

## 4. 테스트와 빌드

```bash
./gradlew test       # 메모리 H2(Oracle 호환 모드) + Fake Agent + 시드. 외부 서비스가 필요 없다
./gradlew build      # 테스트 포함. 결과 jar: build/libs/interview-backend-0.1.0.jar
```

테스트 보고서: `build/reports/tests/test/index.html`

## 5. dev / prod 실행

```bash
export SPRING_PROFILES_ACTIVE=prod,ad
export JWT_SECRET=...  INTERNAL_API_KEY=...  ROOM_TICKET_SECRET=...
export DB_URL=jdbc:oracle:thin:@//db-host:1521/SERVICE  DB_USERNAME=...  DB_PASSWORD=...
export REDIS_SENTINEL_MASTER=mymaster  REDIS_SENTINEL_NODES=h1:26379,h2:26379,h3:26379
export AGENT_BASE_URL=http://agent-host:8000  AGENT_WS_BASE_URL=wss://agent-host  CORS_ALLOWED_ORIGINS=https://front-host
java -jar build/libs/interview-backend-0.1.0.jar
```

- 스키마는 먼저 `db/oracle/schema.sql` 로 만든다([database.md](database.md)). 앱은 `ddl-auto=validate` 로 맞는지 확인만 하고, 다르면 뜨지 않는다.
- `prod` 에서는 `INTERNAL_API_KEY`, `ROOM_TICKET_SECRET` 이 개발용 기본값이면 시작을 거부한다.
- dev-login 은 `local` 에서만 있다. `ad` 프로파일의 SSO 는 아직 자리만 있어서, **지금은 dev/prod 에서 로그인할 방법이 없다**([auth.md](auth.md)).
- 여러 대를 띄우면 서버마다 `APP_INSTANCE_ID` 를 다르게 준다(기본은 호스트 이름).

## 6. 폐쇄망(반입 후)

- **Gradle 배포본**: `gradle/wrapper/gradle-wrapper.properties` 의 `distributionUrl` 을 사내 파일 서버 주소로 바꾸거나, 배포본 zip 을 `~/.gradle/wrapper/dists/` 에 미리 넣는다.
- **의존성**: 사내 Maven 미러를 쓴다.

  ```bash
  ./gradlew build -PmavenRepoUrl=http://사내저장소/repository/maven-public/
  ```

  매번 쓰기 싫으면 `~/.gradle/gradle.properties` 에 `mavenRepoUrl=...` 을 적는다. Gradle 플러그인(Spring Boot 플러그인)도 미러에서 받게 하려면 `settings.gradle` 맨 위에 `pluginManagement { repositories { maven { url '...' } } }` 를 더한다.
- 미러가 없으면 반입 전에 `~/.gradle/caches` 를 통째로 가져가고 `./gradlew build --offline` 으로 빌드한다.
