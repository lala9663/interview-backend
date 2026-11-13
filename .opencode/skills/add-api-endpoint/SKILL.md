---
name: add-api-endpoint
description: interview-backend 에 REST 엔드포인트를 추가하거나 기존 엔드포인트의 동작을 바꿀 때 쓴다. 계약 확인, 패키지 선택, 권한, 오류 응답, 테스트까지의 순서와 이 레포의 관례를 담고 있다.
---

# API 엔드포인트 추가

## 0. 시작 전 확인

1. `docs/contract/api-contract.md` 에 그 경로가 있는가?
   - 있다 → 경로·필드명·열거형을 **그대로** 구현한다.
   - 없다 / 모양이 다르다 → 멈추고 `change-api-contract` skill 을 먼저 따른다.
2. 응답 모양이 계약에 안 적혀 있으면 `docs/open-questions.md` 3장에 이미 정한 모양이 있는지 본다. 새로 정했다면 거기에 적는다.
3. 주인 패키지를 정한다(`com.interview.<feature>`). 경로의 첫 명사가 아니라 **데이터의 주인**이 기준이다.
   예: `PUT /interviews/{id}/interviewers` 는 `interview`, `GET /interviews/{id}/report` 는 `report`.

## 1. 만드는 순서

1. **DTO** — 요청은 `@Getter @Setter` + `javax.validation` 애너테이션, 응답은 `@Getter` + 생성자/정적 팩터리.
   - 필드명은 계약의 camelCase 그대로. null 인 필드도 그대로 내보낸다(`@JsonInclude` 를 붙이지 않는다).
   - 시각은 `OffsetDateTime`(응답: `Times.toOffset(localDateTime)`, 요청: `Times.toLocal(offsetDateTime)`).
   - 목록은 `ListResponse.of(items)`(`{items}`) 또는 `PageResponse`(`{items,page,size,total}`).
   - 엔티티를 그대로 응답으로 내보내지 않는다.
2. **Service** — 업무 규칙과 트랜잭션.
   - 조회는 `@Transactional(readOnly = true)`, 변경은 `@Transactional`.
   - 없는 대상: `throw ApiException.notFound("X_NOT_FOUND", "…을 찾을 수 없습니다.")`
   - 상태 충돌: `ApiException.conflict("ROOM_ALREADY_LIVE", "…")`, 입력 오류: `ApiException.validation("…")`
   - 오류 코드는 문자열로 바로 쓴다. 공용 enum 을 만들지 않는다. 메시지는 한국어.
3. **Controller** — 얇게. `@RestController @RequestMapping("/api/v1")`, 본문은 `@Valid @RequestBody`.
   - 로그인 사용자: `@AuthenticationPrincipal CurrentUser user`
   - 202 를 돌려주는 작업 접수: `ResponseEntity.status(HttpStatus.ACCEPTED).body(singletonMap("jobId", id))`
   - 그 밖의 성공은 200, 삭제는 204.
4. **권한**
   - 계약 표에 (ADMIN) → 컨트롤러 메서드에 `@PreAuthorize("hasRole('ADMIN')")`
   - 특정 면접에 쓰는 동작 → 서비스 첫 줄에 `interviewAccess.requireWritable(interviewId, user)` (배정된 면접관 또는 ADMIN)
   - 조회 → 추가 검사 없음(로그인만)
   - 인증 없이 열어야 하는 경로는 `SecurityConfig` 를 고쳐야 한다. 공용 파일이므로 그 변경만 담은 작은 PR 로 먼저 합친다.
5. **Agent 를 불러야 하면** 요청 스레드에서 부르지 않는다. `AiJobService.submit(type, interviewId, payload)` 로 접수하고 `AiJobHandler` 에서 부른다(`briefing/BriefingJobHandler` 를 본뜬다).

## 2. 지킬 것

- Java 8 문법만(`var`, `List.of`, `isBlank()` 등 금지). 컴파일러가 JDK 8 이라 대부분 걸러진다.
- 조회 조건이 여러 개면 `Specification` 을 쓴다(`InterviewService.list` 참고). 네이티브 SQL 금지. `:param is null or …` 같은 JPQL 은 쓰지 않는다.
- id 목록으로 조회할 때는 `Chunks.of(ids)` 로 나눠 `IN` 에 1000개가 넘지 않게 한다.
- 목록에서 항목마다 추가 조회를 하지 않는다(N+1). `InterviewAssembler` 처럼 한꺼번에 읽어 조립한다.
- 면접자 정보를 내보내는 응답에 블라인드 항목을 넣지 않는다. Agent 로 가는 DTO 에는 이름·수험번호도 넣지 않는다.

## 3. 테스트

`src/test/java/com/interview/support/ApiTestSupport` 를 상속한다.

```java
class MyFeatureTest extends ApiTestSupport {
    @Test
    void works() throws Exception {
        String admin = login("E1001");
        JsonNode body = api(HttpMethod.POST, admin, "/api/v1/...", "{\"a\":1}").expect(200).json;
        assertThat(body.get("a").asInt()).isEqualTo(1);
        api(HttpMethod.POST, login("E1003"), "/api/v1/...", "{\"a\":1}").expect(403);
    }
}
```

- 테스트 클래스들이 DB 를 공유한다. 다른 테스트가 만든 데이터에 기대지 말고, 필요한 데이터는 그 테스트에서 만든다(시드 데이터는 써도 된다).
- 비동기 작업의 결과는 `await("설명", () -> 조건)` 으로 기다린다.
- 최소한: 정상 1건, 권한 거부 1건(해당하면), 없는 대상 404 또는 입력 오류 400 1건.

## 4. 끝내기 전 체크리스트

- [ ] 경로·메서드·필드명·열거형이 계약과 같다
- [ ] 권한(ADMIN / 배정된 면접관)이 걸려 있고 테스트로 확인했다
- [ ] 오류가 `{ code, message, details }` 로 나간다(직접 `ResponseEntity` 로 오류를 만들지 않았다)
- [ ] 엔티티를 바꿨다면 `./gradlew generateOracleSchema` 를 돌렸다
- [ ] `./gradlew build` 통과
- [ ] 문서: 계약에 없는 모양을 정했으면 `docs/open-questions.md`, 흐름이 바뀌었으면 `docs/architecture.md`
