package com.interview.upload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 가상의 샘플 엑셀을 만든다(실제 사람의 정보가 아니다). 헤더는 excel-mapping.yml 의 순서를 따른다.
 *
 * sample(): 가짜 면접자 50행. 일부러 넣은 오류 행 3개:
 *   - 16행: 성명이 비어 있음            → REQUIRED_MISSING
 *   - 29행: 직무 코드가 XX              → UNKNOWN_JOB_CODE
 *   - 42행: 수험번호가 앞의 행과 같음   → DUPLICATE_IN_FILE
 * 직무 코드는 local 시드의 신입 공채(SW, HW, AI, MGMT)에 맞춰져 있다.
 */
@Component
@RequiredArgsConstructor
public class SampleExcelGenerator {

    public static final int SAMPLE_ROWS = 50;
    /** 오류를 넣는 데이터 순번(1부터). 시트의 행 번호는 여기에 헤더 1행을 더한 값이다. */
    public static final int ERROR_NO_NAME = 15;
    public static final int ERROR_BAD_JOB = 28;
    public static final int ERROR_DUPLICATE = 41;

    private static final String[] SURNAMES = { "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임" };
    private static final String[] GIVEN = { "도윤", "서연", "하준", "지우", "민준", "서윤", "예준", "하은", "시우", "지아", "주원", "수아",
            "건우", "다은", "현우", "채원", "지호", "유나", "준서", "소율" };
    private static final String[][] JOBS = { { "SW", "SW개발" }, { "HW", "HW설계" }, { "AI", "AI연구" },
            { "MGMT", "경영지원" } };
    private static final String[] SCHOOLS = { "한빛대학교", "새솔대학교", "가람대학교", "누리대학교", "미르공과대학교" };
    private static final String[][] MAJORS = { { "컴퓨터공학", "자료구조, 운영체제, 데이터베이스, 컴퓨터네트워크" },
            { "전자공학", "회로이론, 디지털논리회로, 신호및시스템, 반도체공학" }, { "인공지능학", "기계학습, 선형대수, 확률통계, 딥러닝" },
            { "경영학", "회계원리, 재무관리, 조직행동론, 마케팅" } };
    private static final String[] PROJECTS = { "캡스톤 디자인에서 4인 팀의 백엔드를 맡아 주문 처리 API 를 설계하고 응답 시간을 40% 줄였습니다.",
            "학부 연구생으로 FPGA 기반 신호 처리 보드를 설계하고 타이밍 오류를 분석해 수정했습니다.", "공개 데이터로 수요 예측 모델을 만들고 특성 선택 실험으로 오차를 12% 줄였습니다.",
            "교내 창업 동아리에서 원가 분석 시트를 만들어 판매 가격 정책을 다시 세웠습니다." };

    private final ExcelMappingLoader mappingLoader;

    public byte[] emptyTemplate() {
        return build(0);
    }

    public byte[] sample() {
        return build(SAMPLE_ROWS);
    }

    private byte[] build(int rowCount) {
        List<ExcelMapping.Column> columns = mappingLoader.get().getColumns();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("면접대상자");
            int headerIndex = mappingLoader.get().getHeaderRow() - 1;
            Row header = sheet.createRow(headerIndex);
            CellStyle bold = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);
            for (int c = 0; c < columns.size(); c++) {
                header.createCell(c).setCellValue(columns.get(c).getHeader());
                header.getCell(c).setCellStyle(bold);
                sheet.setColumnWidth(c, 16 * 256);
            }
            for (int n = 1; n <= rowCount; n++) {
                Map<String, String> values = fakePerson(n);
                Row row = sheet.createRow(headerIndex + n);
                for (int c = 0; c < columns.size(); c++) {
                    ExcelMapping.Column def = columns.get(c);
                    String key = def.isBlind() ? "blind." + def.getBlindKey() : def.getField();
                    if ("essay".equals(key)) {
                        key = "essay." + def.getHeader();
                    }
                    String value = values.get(key);
                    if (value != null && !value.isEmpty()) {
                        row.createCell(c).setCellValue(value);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("샘플 엑셀 생성 실패", e);
        }
    }

    /** n 번째 가짜 면접자(1부터). 키는 매핑의 field (블라인드는 blind.<blindKey>). */
    private Map<String, String> fakePerson(int n) {
        int jobIndex = (n - 1) % JOBS.length;
        Map<String, String> v = new LinkedHashMap<String, String>();
        int applicantSeq = n == ERROR_DUPLICATE ? 3 : n;
        v.put("applicantNo", String.format("2026H-%05d", applicantSeq));
        String name = SURNAMES[(n * 7) % SURNAMES.length] + GIVEN[(n * 3) % GIVEN.length];
        v.put("name", n == ERROR_NO_NAME ? "" : name);
        v.put("jobCode", n == ERROR_BAD_JOB ? "XX" : JOBS[jobIndex][0]);
        v.put("jobTitle", JOBS[jobIndex][1]);

        // 블라인드 항목: 업로드하면 버려지는 값이다. 버려지는 것을 확인할 수 있게 일부러 채운다.
        v.put("blind.gender", n % 2 == 0 ? "여" : "남");
        v.put("blind.birthDate", String.format("%d-%02d-%02d", 1998 + (n % 5), 1 + (n % 12), 1 + (n % 28)));
        v.put("blind.family", "부, 모, 형제 " + (n % 3) + "명");
        v.put("blind.address", "가상시 샘플구 예시로 " + n);
        v.put("blind.hometown", "가상시");

        v.put("education.degree", n % 9 == 0 ? "석사" : "학사");
        v.put("education.school", SCHOOLS[n % SCHOOLS.length]);
        v.put("education.major", MAJORS[jobIndex][0]);
        v.put("education.gpa", String.format("%.1f/4.5", 3.2 + (n % 12) * 0.1));
        v.put("courses", MAJORS[jobIndex][1]);
        v.put("essay.자기소개서1", JOBS[jobIndex][1] + " 직무에 지원한 이유는 전공 수업과 프로젝트에서 이 일이 가장 오래 몰입할 수 있는 일임을 확인했기 때문입니다. (가상 지원자 "
                + n + ")");
        v.put("essay.자기소개서2", "가장 자신 있는 역량은 문제를 작게 나눠 원인을 찾는 것입니다. " + PROJECTS[jobIndex]);
        v.put("essay.자기소개서3", "입사 후 3년 안에 담당 영역을 혼자 책임질 수 있는 수준이 되고, 이후에는 후배의 성장을 돕고 싶습니다.");
        v.put("project", PROJECTS[jobIndex]);
        v.put("certificates", jobIndex == 3 ? "전산회계 1급, 컴퓨터활용능력 1급" : "정보처리기사");
        v.put("extra.languages", "TOEIC " + (780 + (n % 20) * 10));
        v.put("extra.awards", n % 4 == 0 ? "교내 캡스톤 경진대회 우수상" : "");
        return v;
    }
}
