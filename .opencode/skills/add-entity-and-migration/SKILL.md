---
name: add-entity-and-migration
description: JPA 엔티티(테이블)를 새로 만들거나 컬럼을 추가·변경할 때 쓴다. Oracle 에서 깨지지 않게 하는 규칙, db/oracle/schema.sql 재생성, 운영 DB 용 ALTER 문 작성 절차를 담고 있다.
---

# 엔티티 추가·변경과 스키마 반영

local 은 H2(Oracle 호환 모드)라 Oracle 에서만 나는 문제가 로컬에서 안 보인다. 아래 규칙을 지키고 `OracleSchemaTest` 로 확인한다.

## 1. 엔티티 틀

기존 엔티티(`interview/Interview.java`)를 본뜬다.

```java
@Entity
@Table(name = "IV_SOMETHING",
        uniqueConstraints = @UniqueConstraint(name = "UK_IV_SOMETHING_X", columnNames = { "A_ID", "B_NO" }),
        indexes = @Index(name = "IX_IV_SOMETHING_A", columnList = "A_ID"))
@Getter
@Setter
@NoArgsConstructor
public class Something {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivSomethingSeq")
    @SequenceGenerator(name = "ivSomethingSeq", sequenceName = "IV_SOMETHING_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "INTERVIEW_ID", nullable = false)
    private Long interviewId;                       // 다른 엔티티는 id 로만 가리킨다

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private SomethingStatus status;

    @Column(name = "TITLE", length = 200)
    private String title;

    @Lob
    @Column(name = "BODY_TEXT")
    private String bodyText;                        // 1000자를 넘을 수 있는 글

    @Lob
    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "DETAIL_JSON")
    private JsonNode detail;                        // JSON 모양 데이터

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;                // Times.now()
}
```

## 2. Oracle 주의점

| 규칙 | 이유 |
| --- | --- |
| 테이블 이름은 `IV_` 로 시작 | 스키마를 다른 시스템과 함께 쓴다 |
| 테이블·컬럼·인덱스·유니크·시퀀스 이름은 **30자 이내** | Oracle 12.1 이하의 한도. `@Column(name=…)`, `@Index(name=…)`, `@UniqueConstraint(name=…)` 를 직접 적는다 |
| 예약어를 이름으로 쓰지 않는다: USER, COMMENT, SIZE, LEVEL, DATE, NUMBER, ROW, MODE, TYPE, FILE, ORDER, VALUE, KEY, TEXT, START, SESSION, RESOURCE, ACCESS ... | DDL·쿼리가 깨진다. `INTERVIEW_TYPE`, `ROW_NO`, `SOURCE_KIND`, `QUESTION_TEXT`, `ORDER_NO` 처럼 바꾼다 |
| PK 는 `@SequenceGenerator(allocationSize = 1)`. `GenerationType.IDENTITY`, `AUTO` 금지 | Oracle·H2 모두에서 같은 동작 |
| 문자열 `length` 는 **1000 이하**. 더 길면 `@Lob` | VARCHAR2 한도는 4000바이트, 한글은 3바이트 |
| 자유 입력 문자열 컬럼에 `nullable = false` 를 걸지 않는다 | Oracle 에서 빈 문자열은 NULL 이라 `""` 저장이 실패한다 |
| enum 은 `@Enumerated(EnumType.STRING)`, `length = 20` 정도 | 순서(ORDINAL)로 저장하면 값 추가 때 깨진다 |
| `boolean` 컬럼 이름은 `IS_…` | NUMBER(1,0) 으로 만들어진다 |
| 시각은 `LocalDateTime`, 날짜는 `LocalDate`. 컬럼 이름은 `…_AT`, `…_DT` | `DATE` 를 이름으로 못 쓴다. 시간대는 `Times` 가 처리한다 |
| `@ManyToOne` / `@OneToMany` 를 쓰지 않는다 | 이 레포의 규칙(패키지 간 결합, 지연 로딩 문제 방지). FK 제약도 만들지 않는다 |
| JSON 은 CLOB + `JsonNodeConverter`. DB 에서 JSON 내부를 조건으로 조회하지 않는다 | Oracle 버전에 따라 JSON 함수가 다르다. 조회 조건이 될 값은 별도 컬럼으로 뺀다 |
| CLOB 컬럼을 `where`, `order by`, `distinct`, `group by` 에 쓰지 않는다 | Oracle 이 허용하지 않는다 |

쿼리를 함께 만든다면:

- 네이티브 SQL 금지. JPQL·파생 쿼리·`Specification` 만.
- `IN (:ids)` 는 `Chunks.of(ids)` 로 나눈다(1000개 한도).
- 빈 문자열과의 비교(`= ''`)에 기대지 않는다. 파라미터가 null 일 수 있는 조건은 `Specification` 으로 조립한다.
- 대량 삭제는 `@Modifying(clearAutomatically = true, flushAutomatically = true) @Query("delete from …")`.

## 3. 리포지토리

- 엔티티와 같은 패키지에 **최상위 인터페이스**로 만든다(다른 클래스 안의 중첩 인터페이스는 Spring Data 가 찾지 못한다).
- `extends JpaRepository<Something, Long>`, 조건 조합이 필요하면 `JpaSpecificationExecutor<Something>` 도.

## 4. 스키마 반영

```bash
./gradlew generateOracleSchema      # db/oracle/schema.sql 을 다시 만든다 (DB 연결 불필요)
./gradlew test --tests '*OracleSchemaTest*'
```

- `schema.sql` 은 손으로 고치지 않는다. 병합 충돌이 나면 양쪽 엔티티를 살린 뒤 다시 생성한다.
- `OracleSchemaTest` 가 검사하는 것: `IV_` 접두사, 이름 30자, 예약어, VARCHAR 길이 1000, `schema.sql` 이 엔티티와 같은지.
- local DB(`./data/`)는 `ddl-auto=update` 라 **컬럼 추가**는 자동 반영된다. 이름 변경·삭제·타입 변경은 반영되지 않으니 `data/` 를 지우고 다시 띄운다.

## 5. 운영 DB 가 이미 있을 때 (ALTER 문)

dev/prod 는 `ddl-auto=validate` 다. 스키마가 엔티티와 다르면 앱이 뜨지 않는다.

1. `git diff db/oracle/schema.sql` 로 바뀐 부분을 본다.
2. 그 차이를 ALTER 문으로 옮겨 `db/oracle/changes/YYYYMMDD_설명.sql` 로 저장한다.

   ```sql
   -- 20261120_add_interview_location.sql
   alter table iv_interview add (location_name varchar2(200 char));
   create index ix_iv_interview_loc on iv_interview (location_name);
   ```

3. `NOT NULL` 컬럼을 추가할 때는 기본값을 주거나, NULL 허용으로 추가 → 값 채우기 → NOT NULL 로 변경 순서로 한다.
4. 컬럼 이름 변경·삭제는 두 번에 나눈다: (1) 새 컬럼 추가 + 앱이 새 컬럼을 쓰게 배포 (2) 옛 컬럼 삭제.
5. 배포 순서는 ALTER 실행 → 앱 배포.

## 6. 체크리스트

- [ ] 이름 규칙(`IV_`, 30자, 예약어 아님)을 지켰다
- [ ] 시퀀스 PK, `allocationSize = 1`
- [ ] 문자열 길이 1000 이하 또는 `@Lob`
- [ ] 연관 매핑 없이 id 로만 가리킨다
- [ ] `./gradlew generateOracleSchema` 를 돌려 `schema.sql` 을 함께 커밋한다
- [ ] 운영 DB 가 있으면 `db/oracle/changes/` 에 ALTER 문을 넣었다
- [ ] `docs/database.md` 의 테이블 표를 고쳤다
- [ ] local 화면에 보여야 하면 `seed/LocalSeedRunner` 에 가짜 데이터를 더했다
- [ ] `./gradlew build` 통과
