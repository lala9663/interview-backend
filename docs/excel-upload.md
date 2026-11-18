# 엑셀 업로드

`POST /api/v1/recruitments/{id}/uploads` (multipart, 파트 이름 `file`, ADMIN). 한 파일에 면접자 여러 명.

## 1. 처리 순서

1. `upload.ExcelCandidateSource` — 파일을 열고 매핑에 따라 행을 `CandidateRow` 로 바꾼다.
2. `candidate.imports.CandidateImportService` — 행마다 검증하고 통과한 행만 저장한다.
3. `upload.UploadService` — 이력(`IV_UPLOAD_BATCH`)과 실패 행(`IV_UPLOAD_ERROR`)을 남긴다.

한 행이 실패해도 나머지 행은 저장된다. 파일 자체를 못 읽으면 아무것도 저장하지 않고 400 을 돌려준다(이력도 남지 않는다).

## 2. 매핑 파일

`src/main/resources/excel-mapping.yml`. 엑셀 양식이 바뀌면 **코드가 아니라 이 파일을 고친다.**
운영에서 재배포 없이 고치려면 파일을 서버에 두고 `EXCEL_MAPPING_LOCATION=file:/경로/excel-mapping.yml` 로 지정한다(서버를 다시 띄워야 읽는다).

```yaml
sheetIndex: 0      # 읽을 시트 (0 = 첫 시트)
headerRow: 1       # 헤더가 있는 행 (1부터)
columns:
  - { header: 수험번호, field: applicantNo, required: true, maxLength: 50, aliases: [지원번호, 접수번호] }
  - { header: 성별, blind: true, blindKey: gender }
  - { header: 자기소개서1, field: essay, title: 지원동기 }
```

| 키 | 뜻 |
| --- | --- |
| `header` | 엑셀의 헤더 글자. 공백은 무시하고 비교한다 |
| `aliases` | 같은 컬럼의 다른 표기 |
| `field` | 저장할 곳(아래 표). `blind` 컬럼에는 쓰지 않는다 |
| `required` | true 면 헤더가 없을 때 파일 오류(`HEADER_MISSING`), 값이 비면 행 오류(`REQUIRED_MISSING`) |
| `maxLength` | 넘으면 행 오류(`VALUE_TOO_LONG`) |
| `blind` | true 면 **셀을 읽지 않는다.** 파일에 그 컬럼이 있었다는 것만 `blindKey` 로 남긴다 |
| `blindKey` | `blindedFields` 에 남는 이름(예: `gender`) |
| `title` | `essay` / `project` / `career` 의 제목(자소서 문항 제목) |

`field` 값:

| field | 저장되는 곳 |
| --- | --- |
| `applicantNo`, `name`, `jobCode` | 면접자 기본 항목(필수) |
| `jobTitle` | 읽기만 한다. 저장되는 직무명은 등록된 직무(JD)의 제목이다 |
| `applicantType` | 면접자 유형(선택 열, 기본 헤더 "지원유형"). 장학생 / 공채 / 인턴 전환 / 일반 경력 / 박사 / 경력 석사(공백 무시) 또는 `SCHOLARSHIP` / `OPEN` / `INTERN` / `CAREER` / `PHD` / `CAREER_MASTER`. 열이 없거나, 비었거나, 모르는 값이면 행 오류 없이 유형을 넣지 않는다. 이미 유형이 있는 면접자는 그대로 둔다 |
| `education.degree` / `.school` / `.major` / `.gpa` | `profile.education[0]` |
| `courses`, `certificates` | 쉼표·세미콜론·줄바꿈으로 나눠 `profile.courses` / `profile.certificates` |
| `essay` | `profile.essays[]` 에 `{ title, text }`. 컬럼을 여러 개 둘 수 있다 |
| `project` | `profile.projects[]` 에 `{ title, text }` |
| `career` | `profile.careers[]` 에 `{ text }` (회사·직책·기간은 나누지 않는다) |
| `extra.<이름>` | `profile.extra.<이름>` 에 글자 그대로 |

규칙:

- 헤더 순서는 상관없다.
- **매핑에 없는 헤더의 컬럼은 읽지 않고 버린다**(허용 목록 방식). 새 컬럼을 저장하려면 매핑에 추가해야 한다.
- 값이 모두 빈 행은 건너뛴다.
- 셀 값은 화면에 보이는 글자 그대로 읽는다(숫자·날짜 서식 포함).

### 자주 하는 수정

| 하고 싶은 것 | 고칠 곳 |
| --- | --- |
| 헤더 이름이 바뀜 | `header` 를 바꾸거나 `aliases` 에 추가 |
| 자소서 문항이 늘어남 | `- { header: 자기소개서4, field: essay, title: 문항 제목 }` 추가 |
| 블라인드 항목이 늘어남 | `- { header: 병역, blind: true, blindKey: military }` 추가 |
| 새 항목을 저장 | `- { header: 봉사활동, field: extra.volunteer }` 추가 |
| profile 에 새 구조(목록·객체)가 필요 | `CandidateProfile` 에 필드 추가 + `ExcelCandidateSource.toRow` 에 분기 추가 + API 변경 절차 |

고친 뒤에는 `./gradlew test` 를 돌린다. 매핑이 잘못되면(헤더 누락, blind 인데 field 가 있음, 필수 3개 중 하나가 없음, 모르는 키) 서버가 시작할 때 오류로 알려 준다.

## 3. 블라인드 항목

기본 매핑의 블라인드 컬럼: 성별(`gender`), 생년월일(`birthDate`), 가족사항(`family`), 사진(`photo`), 주소(`address`), 출신지역(`hometown`), 나이(`age`).

- 값은 DB 어디에도 저장하지 않는다. 암호화해서 따로 두지도 않는다(버린다).
- 면접자 상세의 `blindedFields` 로 "이 항목이 원본에 있었고 버렸다"는 것만 보여 준다.
- 기준은 현업이 정한다. 기준이 오면 이 파일에 항목을 더한다.
- 자소서 본문 속의 표현(가족 이야기 등)은 여기서 거르지 않는다. Agent 가 요약·질문을 만들 때 거른다.

## 4. 오류 코드

파일 단위(HTTP 400, 아무것도 저장하지 않음):

| code | 뜻 |
| --- | --- |
| `FILE_UNREADABLE` | 파일을 열 수 없다. 엑셀이 아니거나, 암호가 걸렸거나, **DRM(문서 보안)이 풀리지 않은 파일**. 메시지에 DRM 안내가 들어 있다 |
| `FILE_EMPTY` | 시트·헤더·면접자 행이 없다 |
| `HEADER_MISSING` | 필수 컬럼의 헤더가 없다. `details.missingHeaders` 에 목록 |
| `FILE_TOO_MANY_ROWS` | 한 번에 5000행(`app.upload.max-rows`)을 넘는다 |
| `FILE_TOO_LARGE` | 20MB(`spring.servlet.multipart.max-file-size`)를 넘는다 |

행 단위(HTTP 200, 응답의 `errors[]` 와 `GET /uploads/{id}` 에 남는다):

| code | column | 뜻 |
| --- | --- | --- |
| `REQUIRED_MISSING` | 그 컬럼의 헤더 | 필수 값이 비어 있다 |
| `VALUE_TOO_LONG` | 그 컬럼의 헤더 | `maxLength` 를 넘는다 |
| `UNKNOWN_JOB_CODE` | 지원직무코드 | 그 채용에 등록되지 않은 직무 코드 |
| `DUPLICATE_IN_FILE` | 수험번호 | 같은 파일 안에서 앞의 행과 수험번호가 같다(뒤의 행이 오류) |

`errors[]` 의 항목: `{ rowNumber, column, value, code, message }`. `rowNumber` 는 엑셀 시트의 행 번호(헤더가 1행이면 첫 데이터는 2행)다.
`code` 는 API 명세의 예시에 없는 추가 필드다.

같은 채용에 **이미 있는 수험번호**는 오류가 아니다. 새로 만들지 않고 그 면접자를 갱신한다(이름, 직무, profile, blindedFields). 면접·평가는 그대로 남는다.

## 5. DRM

DRM 이 풀린 파일이 들어온다고 가정한다. 백엔드는 DRM 을 풀지 않는다.
못 여는 파일은 `FILE_UNREADABLE` 로 답하고, 메시지로 "DRM 을 해제한 뒤 다시 올리라"고 안내한다. DRM 파일과 깨진 파일을 구분하지는 못한다.

## 6. 샘플 엑셀

`GET /api/v1/uploads/template` (로그인 필요) — `upload.SampleExcelGenerator` 가 만든다.

- 기본: **가짜 면접자 50행.** 헤더는 매핑 파일의 순서대로. 블라인드 컬럼에도 가짜 값을 채워 두어, 올리면 버려지는 것을 확인할 수 있다.
- 일부러 넣은 오류 행 3개: 16행(성명 없음), 29행(직무 코드 `XX`), 42행(수험번호 중복). 올리면 `totalRows 50, successRows 47, failedRows 3`.
- 직무 코드는 local 시드의 신입 공채(SW, HW, AI, MGMT)에 맞춰져 있다. 신입은 기본 설정(`app.live.new-grad-enabled=false`)에서 실시간 기능이 꺼져 있어, 올린 면접자로는 질문 화면과 평가 입력까지만 된다. 전사·보고서까지 보려면 경력 채용의 시드 면접으로 들어간다([setup.md](setup.md) 2장).
- `?empty=true` 를 붙이면 헤더만 있는 빈 양식을 준다(API 명세에 없는 추가 파라미터).

## 7. API 입력(경력용)은 자리만 있다

`candidate.imports.ApiCandidateSource` 가 API 본문을 `CandidateRow` 로 바꾸는 변환만 한다. 이것을 부르는 엔드포인트는 API 명세에 경로가 없어 만들지 않았다.
붙이는 법은 `.opencode/skills/add-candidate-source/SKILL.md` 를 본다. 엑셀이든 API 든 검증·저장은 같은 `CandidateImportService.importRows` 를 거친다.
