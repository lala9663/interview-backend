package com.interview;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interview.candidate.ApplicantType;
import com.interview.candidate.Candidate;
import com.interview.common.Times;
import com.interview.common.json.Json;
import com.interview.recruitment.Track;
import com.interview.report.Report;
import com.interview.report.ReportBasis;
import com.interview.report.ReportDetail;
import com.interview.report.ReportRepository;
import com.interview.support.FixtureTestSupport;

/** API 명세 2.6: 사전 리포트의 previousRoundChecks, 준비된 질문에 대한 의견(question-feedback). */
class BriefingAndFeedbackTest extends FixtureTestSupport {

    @Autowired
    private ReportRepository reportRepository;

    @Test
    void briefingCarriesNextRoundChecksOfThePreviousRound() throws Exception {
        String admin = login("E1001");
        String jobTitle = "앞차수검증직무";
        long recruitmentId = newRecruitment(admin, Track.EXPERIENCED, jobTitle);
        Candidate candidate = newCandidate(recruitmentId, Track.EXPERIENCED, "PREV-1", jobTitle, ApplicantType.CAREER);
        long first = newInterview(admin, candidate.getId(), "JOB");
        long second = newInterview(admin, candidate.getId(), "PERSONALITY");

        // 앞 차수에 보고서가 없으면 null. 사전 리포트를 아직 만들지 않았어도(status null) 필드는 있다.
        JsonNode before = apiGet(admin, "/api/v1/interviews/" + second + "/briefing").expect(200).json;
        assertThat(before.get("status").isNull()).isTrue();
        assertThat(before.get("previousRoundChecks").isNull()).isTrue();

        // 앞 차수 보고서: 1판에는 항목이 없고 최신(2판)에 항목이 있다.
        report(first, 1, new String[0]);
        report(first, 2, new String[] { "정합성 문제를 실제로 겪은 사례", "팀 내 설계 리뷰에서의 역할" });
        JsonNode checks = apiGet(admin, "/api/v1/interviews/" + second + "/briefing").expect(200).json.get("previousRoundChecks");
        assertThat(checks.get("seq").asInt()).isEqualTo(1);
        assertThat(checks.get("type").asText()).isEqualTo("JOB");
        assertThat(checks.get("items")).hasSize(2);
        assertThat(checks.get("items").get(0).asText()).isEqualTo("정합성 문제를 실제로 겪은 사례");

        // 1차 면접에는 앞 차수가 없다.
        assertThat(apiGet(admin, "/api/v1/interviews/" + first + "/briefing").expect(200).json.get("previousRoundChecks").isNull())
                .isTrue();

        // 앞 차수가 취소되면 세지 않는다.
        api(HttpMethod.PATCH, admin, "/api/v1/interviews/" + first, "{\"status\":\"CANCELED\"}").expect(200);
        assertThat(apiGet(admin, "/api/v1/interviews/" + second + "/briefing").expect(200).json.get("previousRoundChecks").isNull())
                .isTrue();
    }

    @Test
    void questionFeedbackIsSavedPerUserAndValidated() throws Exception {
        String admin = login("E1001");
        String jobTitle = "의견검증직무";
        long recruitmentId = newRecruitment(admin, Track.EXPERIENCED, jobTitle);
        Candidate candidate = newCandidate(recruitmentId, Track.EXPERIENCED, "FB-1", jobTitle, null);
        long interviewId = newInterview(admin, candidate.getId(), "JOB", "E1002");
        long otherInterviewId = newInterview(admin, candidate.getId(), "PERSONALITY", "E1002");
        String interviewer = login("E1002");
        long q1 = addQuestion(interviewer, interviewId, "첫 질문");
        long q2 = addQuestion(interviewer, interviewId, "둘째 질문");
        long foreign = addQuestion(interviewer, otherInterviewId, "다른 면접의 질문");
        String url = "/api/v1/interviews/" + interviewId + "/question-feedback";

        // 없으면 빈 값
        JsonNode empty = apiGet(interviewer, url).expect(200).json;
        assertThat(empty.get("interviewId").asLong()).isEqualTo(interviewId);
        assertThat(empty.get("overall").isNull()).isTrue();
        assertThat(empty.get("votes")).isEmpty();
        assertThat(empty.get("updatedAt").isNull()).isTrue();

        // 저장하고 다시 읽는다
        JsonNode saved = api(HttpMethod.PUT, interviewer, url, "{\"overall\":\"GOOD\",\"votes\":[{\"questionId\":" + q1
                + ",\"vote\":\"UP\"},{\"questionId\":" + q2 + ",\"vote\":\"DOWN\"}]}").expect(200).json;
        assertThat(saved.get("overall").asText()).isEqualTo("GOOD");
        assertThat(saved.get("votes")).hasSize(2);
        assertThat(saved.get("updatedAt").asText()).isNotEmpty();
        JsonNode read = apiGet(interviewer, url).expect(200).json;
        assertThat(read.get("votes").get(0).get("questionId").asLong()).isEqualTo(q1);
        assertThat(read.get("votes").get(0).get("vote").asText()).isEqualTo("UP");
        assertThat(read.get("votes").get(1).get("vote").asText()).isEqualTo("DOWN");

        // 통째로 바꾼다: overall 을 비우고 의견 하나만 남긴다
        JsonNode replaced = api(HttpMethod.PUT, interviewer, url, "{\"overall\":null,\"votes\":[{\"questionId\":" + q2
                + ",\"vote\":\"UP\"}]}").expect(200).json;
        assertThat(replaced.get("overall").isNull()).isTrue();
        assertThat(replaced.get("votes")).hasSize(1);

        // 의견은 사용자마다 따로다
        assertThat(apiGet(admin, url).expect(200).json.get("votes")).isEmpty();

        // 다른 면접의 질문, 같은 질문 두 번, 모르는 값은 400
        assertThat(api(HttpMethod.PUT, interviewer, url, "{\"votes\":[{\"questionId\":" + foreign + ",\"vote\":\"UP\"}]}")
                .expect(400).json.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        api(HttpMethod.PUT, interviewer, url, "{\"votes\":[{\"questionId\":" + q1 + ",\"vote\":\"UP\"},{\"questionId\":" + q1
                + ",\"vote\":\"DOWN\"}]}").expect(400);
        api(HttpMethod.PUT, interviewer, url, "{\"votes\":[{\"questionId\":" + q1 + ",\"vote\":\"MAYBE\"}]}").expect(400);
        api(HttpMethod.PUT, interviewer, url, "{\"overall\":\"GREAT\"}").expect(400);
        // 실패한 요청은 저장된 것을 바꾸지 않는다
        assertThat(apiGet(interviewer, url).expect(200).json.get("votes")).hasSize(1);

        // votes 에 빈(null) 항목이 오면 500 이 아니라 400
        assertThat(api(HttpMethod.PUT, interviewer, url, "{\"votes\":[null]}").expect(400).json.get("code").asText())
                .isEqualTo("VALIDATION_FAILED");

        // 사전 리포트를 다시 만들어 사라진 질문의 의견은 빼고 돌려준다.
        generateBriefing(admin, interviewId);
        long generated = 0;
        for (JsonNode q : apiGet(admin, "/api/v1/interviews/" + interviewId + "/questions").expect(200).json.get("items")) {
            if ("JD".equals(q.get("source").asText())) {
                generated = q.get("id").asLong();
                break;
            }
        }
        assertThat(api(HttpMethod.PUT, interviewer, url, "{\"overall\":\"SOSO\",\"votes\":[{\"questionId\":" + generated
                + ",\"vote\":\"DOWN\"},{\"questionId\":" + q2 + ",\"vote\":\"UP\"}]}").expect(200).json.get("votes")).hasSize(2);
        generateBriefing(admin, interviewId); // 아직 묻지 않은 준비된 질문은 지워지고 새로 만들어진다
        JsonNode afterRegenerate = apiGet(interviewer, url).expect(200).json;
        assertThat(afterRegenerate.get("overall").asText()).isEqualTo("SOSO");
        assertThat(afterRegenerate.get("votes")).hasSize(1);
        assertThat(afterRegenerate.get("votes").get(0).get("questionId").asLong()).isEqualTo(q2);

        // 배정되지 않은 면접관은 저장할 수 없다(읽기는 된다)
        String outsider = login("E1005");
        api(HttpMethod.PUT, outsider, url, "{\"overall\":\"BAD\"}").expect(403);
        apiGet(outsider, url).expect(200);
        apiGet(admin, "/api/v1/interviews/99999999/question-feedback").expect(404);
    }

    private long addQuestion(String token, long interviewId, String text) throws Exception {
        return api(HttpMethod.POST, token, "/api/v1/interviews/" + interviewId + "/questions", "{\"text\":\"" + text + "\"}")
                .expect(200).json.get("id").asLong();
    }

    private void report(long interviewId, int version, String[] nextRoundChecks) {
        ObjectNode content = Json.object();
        content.put("headline", "테스트 보고서");
        for (String check : nextRoundChecks) {
            content.withArray("nextRoundChecks").add(check);
        }
        Report r = new Report();
        r.setInterviewId(interviewId);
        r.setVersion(version);
        r.setBasis(ReportBasis.LIVE);
        r.setDetail(ReportDetail.DETAILED);
        r.setGeneratedAt(Times.now());
        r.setModel("test");
        r.setContent(content);
        reportRepository.save(r);
    }
}
