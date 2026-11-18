package com.interview.briefing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.candidate.Candidate;
import com.interview.recruitment.Track;
import com.interview.support.FixtureTestSupport;

/** 사전 리포트의 요약과 질문을 따로 다시 만들기(API 명세 2.6 의 target). */
class BriefingTargetTest extends FixtureTestSupport {

    private void generate(final String token, long interviewId, String target) throws Exception {
        final long jobId = api(HttpMethod.POST, token, "/api/v1/interviews/" + interviewId + "/briefing/generate",
                "{\"target\":\"" + target + "\"}").expect(202).json.get("jobId").asLong();
        await("브리핑 작업 " + jobId, () -> "SUCCEEDED".equals(apiGet(token, "/api/v1/ai-jobs/" + jobId).json.path("status").asText()));
    }

    private JsonNode briefing(String token, long interviewId) throws Exception {
        return apiGet(token, "/api/v1/interviews/" + interviewId + "/briefing").expect(200).json;
    }

    private long firstQuestionId(String token, long interviewId) throws Exception {
        return apiGet(token, "/api/v1/interviews/" + interviewId + "/questions").expect(200).json.get("items").get(0).get("id").asLong();
    }

    @Test
    void summaryAndQuestionsAreRegeneratedSeparately() throws Exception {
        String admin = login("E1001");
        String jobTitle = "따로만들기직무";
        long recruitmentId = newRecruitment(admin, Track.EXPERIENCED, jobTitle);
        Candidate candidate = newCandidate(recruitmentId, Track.EXPERIENCED, "TARGET-1", jobTitle, null);
        long interviewId = newInterview(admin, candidate.getId(), "JOB");

        generateBriefing(admin, interviewId);
        JsonNode first = briefing(admin, interviewId);
        assertThat(first.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(first.get("generating").isNull()).isTrue();
        String generatedAt = first.get("generatedAt").asText();
        long questionId = firstQuestionId(admin, interviewId);

        // 질문만: 질문은 새로 만들어지고(id 가 바뀐다) 요약과 만든 시각은 그대로다.
        generate(admin, interviewId, "QUESTIONS");
        JsonNode afterQuestions = briefing(admin, interviewId);
        assertThat(afterQuestions.get("generatedAt").asText()).isEqualTo(generatedAt);
        assertThat(afterQuestions.get("summary").get("headline").asText()).isEqualTo(first.get("summary").get("headline").asText());
        long newQuestionId = firstQuestionId(admin, interviewId);
        assertThat(newQuestionId).isNotEqualTo(questionId);

        // 요약만: 질문은 그대로다(id 가 같다).
        generate(admin, interviewId, "SUMMARY");
        assertThat(briefing(admin, interviewId).get("summary").get("headline").asText()).isNotEmpty();
        assertThat(firstQuestionId(admin, interviewId)).isEqualTo(newQuestionId);
    }

    @Test
    void unknownTargetIsRejected() throws Exception {
        String admin = login("E1001");
        String jobTitle = "따로만들기직무2";
        long recruitmentId = newRecruitment(admin, Track.EXPERIENCED, jobTitle);
        Candidate candidate = newCandidate(recruitmentId, Track.EXPERIENCED, "TARGET-2", jobTitle, null);
        long interviewId = newInterview(admin, candidate.getId(), "JOB");
        api(HttpMethod.POST, admin, "/api/v1/interviews/" + interviewId + "/briefing/generate", "{\"target\":\"REPORT\"}").expect(400);
    }
}
