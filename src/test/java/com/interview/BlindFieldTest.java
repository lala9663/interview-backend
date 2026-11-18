package com.interview;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.agent.FakeAgentClient;
import com.interview.candidate.Candidate;
import com.interview.candidate.CandidateRepository;
import com.interview.common.json.Json;
import com.interview.support.ApiTestSupport;

/**
 * 블라인드 항목(성별, 생년월일, 가족사항, 주소 ...)이 어디에도 남지 않는지 검증한다:
 * DB(profile JSON), 프론트 응답, Agent 로 나가는 요청, Agent 가 읽는 방 context.
 */
class BlindFieldTest extends ApiTestSupport {

    /** 엑셀에 넣는 블라인드 값. 다른 글자와 겹치지 않는 표식이라 어디서든 찾을 수 있다. */
    private static final String GENDER = "성별표식QX";
    private static final String BIRTH = "1999-12-31";
    private static final String FAMILY = "가족표식ZW 부모와 동생";
    private static final String ADDRESS = "주소표식VK 가상시";
    private static final String UNKNOWN_COLUMN = "매핑에없는값JH";
    private static final String NAME = "블라인드검증";
    private static final String APPLICANT_NO = "BLIND-0001";

    @Autowired
    private FakeAgentClient fakeAgent;

    @Autowired
    private CandidateRepository candidateRepository;

    @Test
    void blindedValuesNeverLeaveTheUploadStep() throws Exception {
        final String admin = login("E1001");
        Long recruitmentId = newGradRecruitmentId(admin);

        // 헤더 순서를 일부러 섞고, 블라인드 컬럼과 매핑에 없는 컬럼을 넣는다.
        String[] headers = { "성별", "지원직무코드", "가족사항", "성명", "생년월일", "수험번호", "주소", "비고(매핑없음)", "자기소개서1", "전공" };
        String[] values = { GENDER, "SW", FAMILY, NAME, BIRTH, APPLICANT_NO, ADDRESS, UNKNOWN_COLUMN, "지원동기 본문입니다", "컴퓨터공학" };
        JsonNode batch = upload(admin, recruitmentId, "blind.xlsx", workbook(headers, values)).expect(200).json;
        assertThat(batch.get("successRows").asInt()).isEqualTo(1);
        assertThat(batch.get("failedRows").asInt()).isZero();

        // DB: 값은 없고 "무엇을 버렸는지"만 남는다.
        Candidate candidate = candidateRepository.findByRecruitmentIdAndApplicantNo(recruitmentId, APPLICANT_NO).get();
        String storedProfile = Json.write(candidate.getProfile());
        assertNoBlindValue(storedProfile);
        assertThat(candidate.getBlindedFields()).containsExactlyInAnyOrder("gender", "family", "birthDate", "address");
        assertThat(candidate.getProfile().getEssays().get(0).getTitle()).isEqualTo("지원동기");
        assertThat(candidate.getProfile().getEducation().get(0).getMajor()).isEqualTo("컴퓨터공학");

        // 프론트 응답
        String detail = apiGet(admin, "/api/v1/candidates/" + candidate.getId()).expect(200).text;
        assertNoBlindValue(detail);
        assertThat(detail).contains("\"blindedFields\"").contains("gender");
        assertNoBlindValue(apiGet(admin, "/api/v1/recruitments/" + recruitmentId + "/candidates?q=" + APPLICANT_NO).expect(200).text);

        // Agent 로 나가는 브리핑 요청: 블라인드 값도, 이름·수험번호도 없다.
        long interviewId = api(HttpMethod.POST, admin, "/api/v1/candidates/" + candidate.getId() + "/interviews",
                "{\"type\":\"JOB\",\"scheduledAt\":\"2026-11-20T10:00:00+09:00\"}").expect(200).json.get("id").asLong();
        fakeAgent.clearRecorded();
        final long jobId = api(HttpMethod.POST, admin, "/api/v1/interviews/" + interviewId + "/briefing/generate", null)
                .expect(202).json.get("jobId").asLong();
        await("브리핑", () -> "SUCCEEDED".equals(apiGet(admin, "/api/v1/ai-jobs/" + jobId).json.path("status").asText()));
        String sentToAgent = findRequestContaining("지원동기 본문입니다");
        assertNoBlindValue(sentToAgent);
        assertThat(sentToAgent).doesNotContain(NAME).doesNotContain(APPLICANT_NO);

        // 브리핑 응답과 Agent 가 읽는 방 context
        String briefing = apiGet(admin, "/api/v1/interviews/" + interviewId + "/briefing").expect(200).text;
        assertNoBlindValue(briefing);
        assertThat(briefing).contains("gender").contains("family");
        long roomId = api(HttpMethod.POST, admin, "/api/v1/interviews/" + interviewId + "/room", null).expect(200).json.get("id")
                .asLong();
        String context = internal(HttpMethod.GET, "/internal/v1/rooms/" + roomId + "/context", null).expect(200).text;
        assertNoBlindValue(context);
        assertThat(context).doesNotContain(NAME).doesNotContain(APPLICANT_NO);
    }

    private String findRequestContaining(String marker) {
        List<Object> recorded = fakeAgent.getRecorded();
        for (Object request : recorded) {
            String json = Json.write(request);
            if (json.contains(marker)) {
                return json;
            }
        }
        throw new AssertionError("Fake Agent 가 받은 요청 가운데 해당 면접자의 것이 없습니다. 받은 요청 수: " + recorded.size());
    }

    private static void assertNoBlindValue(String text) {
        assertThat(text).doesNotContain(GENDER).doesNotContain(BIRTH).doesNotContain(FAMILY).doesNotContain("가족표식")
                .doesNotContain(ADDRESS).doesNotContain(UNKNOWN_COLUMN);
    }

    private static byte[] workbook(String[] headers, String[] values) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("시트1");
            Row header = sheet.createRow(0);
            Row row = sheet.createRow(1);
            for (int c = 0; c < headers.length; c++) {
                header.createCell(c).setCellValue(headers[c]);
                row.createCell(c).setCellValue(values[c]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
