package com.interview.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.recruitment.Track;
import com.interview.support.FixtureTestSupport;

/** 면접자 유형(API 명세의 ApplicantType): 엑셀의 선택 열로 들어오고, 목록·상세·면접 응답에 나오며, 목록을 거를 수 있다. */
class ApplicantTypeTest extends FixtureTestSupport {

    @Test
    void parsesKoreanLabelsAndEnumNames() {
        assertThat(ApplicantType.parse("장학생")).isEqualTo(ApplicantType.SCHOLARSHIP);
        assertThat(ApplicantType.parse("공채")).isEqualTo(ApplicantType.OPEN);
        assertThat(ApplicantType.parse("인턴 전환")).isEqualTo(ApplicantType.INTERN);
        assertThat(ApplicantType.parse("인턴전환")).isEqualTo(ApplicantType.INTERN);
        assertThat(ApplicantType.parse("일반 경력")).isEqualTo(ApplicantType.CAREER);
        assertThat(ApplicantType.parse("박사")).isEqualTo(ApplicantType.PHD);
        assertThat(ApplicantType.parse("경력 석사")).isEqualTo(ApplicantType.CAREER_MASTER);
        assertThat(ApplicantType.parse("career_master")).isEqualTo(ApplicantType.CAREER_MASTER);
        assertThat(ApplicantType.parse("모르는값")).isNull();
        assertThat(ApplicantType.parse("  ")).isNull();
        assertThat(ApplicantType.parse(null)).isNull();
    }

    @Test
    void excelColumnFillsTypeAndListCanBeFiltered() throws Exception {
        String admin = login("E1001");
        long recruitmentId = newRecruitment(admin, Track.NEW_GRAD, "유형검증직무");

        // "지원유형" 열이 있는 파일: 한국어 이름, enum 이름, 모르는 값, 빈 칸
        String[] headers = { "수험번호", "성명", "지원직무코드", "지원유형" };
        String[][] rows = { { "TYPE-1", "가상하나", JOB_CODE, "공채" }, { "TYPE-2", "가상둘", JOB_CODE, "인턴 전환" },
                { "TYPE-3", "가상셋", JOB_CODE, "SCHOLARSHIP" }, { "TYPE-4", "가상넷", JOB_CODE, "모르는값" },
                { "TYPE-5", "가상다섯", JOB_CODE, "" } };
        JsonNode batch = upload(admin, recruitmentId, "types.xlsx", workbook(headers, rows)).expect(200).json;
        // 모르는 값은 행 오류가 아니다(유형만 비워 둔다).
        assertThat(batch.get("successRows").asInt()).isEqualTo(5);
        assertThat(batch.get("failedRows").asInt()).isZero();

        String listUrl = "/api/v1/recruitments/" + recruitmentId + "/candidates";
        Map<String, String> typeByNo = new HashMap<String, String>();
        Map<String, Long> idByNo = new HashMap<String, Long>();
        for (JsonNode item : apiGet(admin, listUrl).expect(200).json.get("items")) {
            typeByNo.put(item.get("applicantNo").asText(), item.get("applicantType").isNull() ? null : item.get("applicantType").asText());
            idByNo.put(item.get("applicantNo").asText(), item.get("id").asLong());
        }
        assertThat(typeByNo).containsEntry("TYPE-1", "OPEN").containsEntry("TYPE-2", "INTERN").containsEntry("TYPE-3", "SCHOLARSHIP")
                .containsEntry("TYPE-4", null).containsEntry("TYPE-5", null);

        // 거르기
        JsonNode open = apiGet(admin, listUrl + "?applicantType=OPEN").expect(200).json;
        assertThat(open.get("total").asInt()).isEqualTo(1);
        assertThat(open.get("items").get(0).get("applicantNo").asText()).isEqualTo("TYPE-1");
        assertThat(apiGet(admin, listUrl + "?applicantType=PHD").expect(200).json.get("total").asInt()).isZero();
        assertThat(apiGet(admin, listUrl + "?applicantType=NOPE").expect(400).json.get("code").asText()).isEqualTo("VALIDATION_FAILED");

        // 상세와 면접 응답
        long candidateId = idByNo.get("TYPE-2");
        assertThat(apiGet(admin, "/api/v1/candidates/" + candidateId).expect(200).json.get("applicantType").asText())
                .isEqualTo("INTERN");
        long interviewId = newInterview(admin, candidateId, "JOB");
        assertThat(apiGet(admin, "/api/v1/interviews/" + interviewId).expect(200).json.get("candidate").get("applicantType").asText())
                .isEqualTo("INTERN");

        // 유형 열이 없는 파일을 다시 올려도 그대로 올라가고, 이미 넣은 유형은 지워지지 않는다.
        String[] oldHeaders = { "수험번호", "성명", "지원직무코드" };
        String[][] oldRows = { { "TYPE-1", "가상하나", JOB_CODE }, { "TYPE-6", "가상여섯", JOB_CODE } };
        JsonNode again = upload(admin, recruitmentId, "no-type-column.xlsx", workbook(oldHeaders, oldRows)).expect(200).json;
        assertThat(again.get("successRows").asInt()).isEqualTo(2);
        assertThat(candidateRepository.findByRecruitmentIdAndApplicantNo(recruitmentId, "TYPE-1").get().getApplicantType())
                .isEqualTo(ApplicantType.OPEN);
        assertThat(candidateRepository.findByRecruitmentIdAndApplicantNo(recruitmentId, "TYPE-6").get().getApplicantType()).isNull();
    }

    private static byte[] workbook(String[] headers, String[][] rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("시트1");
            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                header.createCell(c).setCellValue(headers[c]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
