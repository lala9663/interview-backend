---
name: add-candidate-source
description: 면접자 입력 경로를 바꾸거나 늘릴 때 쓴다. 엑셀 양식(컬럼, 자소서 문항, 블라인드 항목)이 바뀐 경우와, 엑셀 대신 API 등 새 경로로 면접자를 받는 경우(경력 채용)의 절차를 담고 있다. 경력은 채용 시스템의 읽기 전용 DB 테이블에서 읽기로 했다(11/18 회의. 받은 것은 아직 없다. 지금 코드의 자리는 API용 변환 클래스다).
---

# 면접자 입력 경로 변경·추가

구조:

```
입력(엑셀 파일, API 본문, …)
   └─ CandidateSource.read()            입력 → List<CandidateRow>   ← 경로마다 다른 부분
        └─ CandidateImportService.importRows()   검증 + 저장          ← 모든 경로 공통
```

- `candidate/imports/CandidateSource` — 인터페이스
- `upload/ExcelCandidateSource` — 엑셀(지금 쓰는 경로). 매핑은 `src/main/resources/excel-mapping.yml`
- `candidate/imports/ApiCandidateSource` — API 본문 → `CandidateRow` 변환만 있다(엔드포인트 없음)
- `candidate/imports/CandidateImportService` — 직무 코드 확인, 파일 내 중복, 같은 수험번호 갱신, 저장

어느 경로든 **검증·저장 규칙은 `CandidateImportService` 한곳**에 둔다. 경로마다 따로 구현하지 않는다.

## A. 엑셀 양식이 바뀌었다

대부분 **코드 수정 없이** `excel-mapping.yml` 만 고치면 된다. 자세한 설명은 `docs/excel-upload.md`.

| 바뀐 것 | 고칠 곳 |
| --- | --- |
| 헤더 이름 | 그 컬럼의 `header` 를 바꾸거나 `aliases` 에 추가 |
| 컬럼 순서 | 고칠 것 없음(헤더 글자로 찾는다) |
| 헤더가 2행에 있음 / 다른 시트 | `headerRow`, `sheetIndex` |
| 자소서 문항 추가·제목 변경 | `- { header: 자기소개서4, field: essay, title: 문항 제목 }` |
| 블라인드 항목 추가 | `- { header: 병역, blind: true, blindKey: military }` |
| 저장할 단순 항목 추가 | `- { header: 봉사활동, field: extra.volunteer }` |
| 필수 여부·최대 길이 | `required`, `maxLength` |

주의:

- **매핑에 없는 헤더는 읽지 않고 버린다.** 새 컬럼을 저장하려면 반드시 매핑에 추가한다.
- 현업이 새 양식을 주면 **블라인드 대상인지부터** 확인한다. 애매하면 `blind: true` 로 두고 물어본다(버린 것은 다시 올리면 되지만, 저장한 것은 지워야 한다).
- `blind` 컬럼에는 `field` 를 주지 않는다(시작할 때 오류로 막는다).

`extra.*` 로 부족하고 profile 에 새 구조(목록·객체)가 필요하면:

1. `candidate/CandidateProfile` 에 필드를 더한다. 프론트가 보는 응답 모양이 바뀌므로 `change-api-contract` 절차를 따른다.
2. `upload/ExcelCandidateSource.toRow` 에 새 `field` 이름의 분기를 더한다.
3. `excel-mapping.yml` 머리말의 field 목록과 `docs/excel-upload.md` 의 표를 고친다.
4. Agent 가 그 항목을 요약·질문에 쓰게 하려면 agent 레포도 알아야 한다(브리핑 요청의 `candidate.profile` 에 그대로 실려 간다).

확인:

```bash
./gradlew test --tests '*BlindFieldTest*' --tests '*CoreFlow*' --tests '*SecurityAndUploadErrorTest*'
```

- 샘플 엑셀(`upload/SampleExcelGenerator`)은 매핑의 헤더 순서를 따라 만들어진다. 새 `field` 에 가짜 값을 넣으려면 `fakePerson` 에 `v.put("<field>", "…")` 를 더한다. 필수 컬럼을 늘렸다면 반드시 더한다(안 그러면 샘플 50행이 모두 오류가 되어 `CoreFlowIntegrationTest` 가 실패한다).

## B. 새 입력 경로를 붙인다 (예: 경력 채용을 외부 시스템이 API 로 보냄)

1. **API 명세부터.** 경로와 본문 모양이 `docs/contract/api-contract.md` 에 없으면 `change-api-contract` 를 먼저 따른다.
   누가 부르는지에 따라 위치가 다르다: 사람(프론트)이면 `/api/v1`(JWT, ADMIN), 다른 시스템이면 `/internal/v1`(`X-Internal-Key`) 또는 별도 인증.
2. **CandidateSource 구현.**
   - API 본문이면 `ApiCandidateSource` 를 그대로 쓰거나 고친다. `Payload` 가 받는 모양이다.
   - 다른 형식(CSV, 다른 시스템의 JSON)이면 `CandidateSource<입력타입>` 구현을 새로 만든다. 기존 구현을 고치지 않는다.
   - `read()` 가 할 일: 입력 한 건 → `CandidateRow`(rowNumber, applicantNo, name, jobCode, profile, blindedFields). 필수 값이 없으면 예외를 던지지 말고 `row.getErrors()` 에 `RowError` 를 넣는다. 입력 전체를 못 읽을 때만 `ApiException`.
   - **블라인드**: 받는 DTO 에 블라인드 항목 필드를 두지 않는다(보내는 쪽이 넣어 보내도 역직렬화에서 버려진다). 무엇이 버려졌는지는 `blindedFields` 로 남긴다.
3. **컨트롤러.**

   ```java
   List<CandidateRow> rows = apiCandidateSource.read(request.getItems());
   ImportResult result = importService.importRows(recruitmentId, apiCandidateSource.sourceType(), rows);
   ```

   - 응답에 `result` 의 `totalRows / successRows / failedRows / errors` 를 담는다.
   - 엑셀 업로드처럼 이력을 남기려면 `upload/UploadService.upload` 의 뒷부분(`UploadBatch`, `UploadError` 저장)을 본뜬다.
4. **덜 끝났으면 기능 플래그 뒤에.** `app.features.candidate-api-import: false` + 컨트롤러에 `@ConditionalOnProperty(name = "app.features.candidate-api-import", havingValue = "true")`.
5. **테스트.** `BlindFieldTest` 를 본떠, 블라인드 값을 넣어 보내도 DB·응답·Agent 요청에 남지 않는지 확인한다. 같은 수험번호를 두 번 보내면 갱신되는지도 본다.

## C. 검증·저장 규칙을 바꾼다

`CandidateImportService.importRows` 를 고친다. 모든 경로에 적용된다.

- 새 행 오류 코드는 `candidate/imports/RowError` 에 상수로 더하고 `docs/excel-upload.md` 4장의 표에 적는다.
- 지금의 규칙: 등록된 직무 코드만 허용, 입력 안의 수험번호 중복은 뒤의 행이 오류, 이미 있는 수험번호는 갱신, 직무명은 등록된 JD 의 제목을 쓴다. 바꾸면 `docs/open-questions.md` 1장도 고친다.

## 체크리스트

- [ ] 새 컬럼·필드가 블라인드 대상인지 확인했다
- [ ] 검증·저장 규칙을 경로별로 따로 구현하지 않았다
- [ ] `BlindFieldTest` 통과(블라인드 값이 DB·응답·Agent 요청에 없다)
- [ ] 샘플 엑셀이 여전히 성공 47·오류 3 이다(`CoreFlowIntegrationTest`)
- [ ] `docs/excel-upload.md` 를 고쳤다
- [ ] `./gradlew build` 통과
