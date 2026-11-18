package com.interview;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.agent.FakeAgentClient;
import com.interview.agent.dto.AgentDtos;
import com.interview.candidate.ApplicantType;
import com.interview.candidate.Candidate;
import com.interview.evaluation.EvaluationProperties;
import com.interview.interview.LiveProperties;
import com.interview.recruitment.Track;
import com.interview.support.FixtureTestSupport;

/**
 * 실시간 기능 잠금(liveEnabled, API 명세 2.5·2.7·4.1)과 AI 참고 평가 권한(2.8).
 * 기본 설정: 신입은 실시간 기능을 쓰지 않고(app.live.new-grad-enabled=false), 경력은 쓴다.
 */
class LiveDisabledTest extends FixtureTestSupport {

    @Autowired
    private FakeAgentClient fakeAgent;

    @Autowired
    private EvaluationProperties evaluationProps;

    @Autowired
    private LiveProperties liveProps;

    private EvaluationProperties.Audience audienceBefore;
    private boolean newGradLiveBefore;
    private boolean experiencedLiveBefore;

    /** 이 테스트가 보는 것은 기본 설정의 동작이다. 설정을 기본값으로 맞추고, 끝나면 테스트 전의 값으로 되돌린다. */
    @BeforeEach
    void useDefaults() {
        audienceBefore = evaluationProps.getAiVisibleTo();
        newGradLiveBefore = liveProps.isNewGradEnabled();
        experiencedLiveBefore = liveProps.isExperiencedEnabled();
        evaluationProps.setAiVisibleTo(EvaluationProperties.Audience.ADMIN);
        liveProps.setNewGradEnabled(false);
        liveProps.setExperiencedEnabled(true);
    }

    @AfterEach
    void restore() {
        evaluationProps.setAiVisibleTo(audienceBefore);
        liveProps.setNewGradEnabled(newGradLiveBefore);
        liveProps.setExperiencedEnabled(experiencedLiveBefore);
    }

    @Test
    void newGradInterviewGetsPredictedFollowUpsAndNoLiveFeatures() throws Exception {
        final String admin = login("E1001");
        String jobTitle = "잠금검증직무";
        long recruitmentId = newRecruitment(admin, Track.NEW_GRAD, jobTitle);
        Candidate candidate = newCandidate(recruitmentId, Track.NEW_GRAD, "LIVE-OFF-1", jobTitle, ApplicantType.OPEN);
        final long interviewId = newInterview(admin, candidate.getId(), "JOB", "E1002");
        assertThat(apiGet(admin, "/api/v1/interviews/" + interviewId).expect(200).json.get("liveEnabled").asBoolean()).isFalse();

        // ---- 브리핑 요청에 predictedFollowUps: 3 과 applicantType 이 실린다
        fakeAgent.clearRecorded();
        generateBriefing(admin, interviewId);
        AgentDtos.BriefingRequest sent = briefingRequestOf(jobTitle);
        assertThat(sent.getPredictedFollowUps()).isEqualTo(3);
        assertThat(sent.getApplicantType()).isEqualTo(ApplicantType.OPEN);

        // ---- 예상 꼬리질문: 질문마다 3개, 그 질문을 부모로 하는 FOLLOW_UP
        JsonNode questions = apiGet(admin, "/api/v1/interviews/" + interviewId + "/questions").expect(200).json.get("items");
        int parents = 0;
        int predicted = 0;
        long firstPredictedId = 0;
        for (JsonNode q : questions) {
            if ("FOLLOW_UP".equals(q.get("source").asText())) {
                predicted++;
                assertThat(q.get("category").asText()).isEqualTo("예상 꼬리질문");
                assertThat(parentOf(questions, q.get("parentQuestionId").asLong())).isNotNull();
                if (firstPredictedId == 0) {
                    firstPredictedId = q.get("id").asLong();
                }
            } else {
                parents++;
                // 준비된 질문이 앞에 오고(1부터) 예상 꼬리질문은 그 뒤에 온다.
                assertThat(q.get("orderNo").asInt()).isEqualTo(parents);
            }
        }
        assertThat(parents).isGreaterThanOrEqualTo(10);
        assertThat(predicted).isEqualTo(parents * 3);

        // ---- 다시 만들어도 예상 꼬리질문이 쌓이지 않는다. 이미 물은 것은 남고, 부모가 지워졌으므로 부모 없이 남는다.
        api(HttpMethod.PATCH, admin, "/api/v1/questions/" + firstPredictedId, "{\"asked\":true}").expect(200);
        generateBriefing(admin, interviewId);
        JsonNode again = apiGet(admin, "/api/v1/interviews/" + interviewId + "/questions").expect(200).json.get("items");
        assertThat(again).hasSize(parents + predicted + 1);
        for (JsonNode q : again) {
            if (q.get("id").asLong() == firstPredictedId) {
                assertThat(q.get("asked").asBoolean()).isTrue();
                assertThat(q.get("parentQuestionId").isNull()).isTrue();
            }
        }

        // ---- 방: 만들고 시작할 수 있지만 입장권은 409 LIVE_DISABLED
        String interviewer = login("E1002");
        long roomId = api(HttpMethod.POST, interviewer, "/api/v1/interviews/" + interviewId + "/room", null).expect(200).json
                .get("id").asLong();
        api(HttpMethod.POST, interviewer, "/api/v1/rooms/" + roomId + "/start", null).expect(200);
        assertThat(api(HttpMethod.POST, interviewer, "/api/v1/rooms/" + roomId + "/ticket", "{\"audio\":true}").expect(409).json
                .get("code").asText()).isEqualTo("LIVE_DISABLED");

        // ---- 종료: 상태만 바뀌고 Agent 마무리·AI 평가·보고서 작업이 없다
        fakeAgent.clearRecorded();
        assertThat(api(HttpMethod.POST, interviewer, "/api/v1/rooms/" + roomId + "/end", null).expect(200).json.get("status")
                .asText()).isEqualTo("ENDED");
        assertThat(apiGet(admin, "/api/v1/interviews/" + interviewId).expect(200).json.get("status").asText())
                .isEqualTo("EVALUATING");
        for (JsonNode job : apiGet(admin, "/api/v1/interviews/" + interviewId + "/jobs").expect(200).json.get("items")) {
            assertThat(job.get("type").asText()).isEqualTo("BRIEFING");
        }
        assertThat(fakeAgent.getFinalizedRooms()).doesNotContain(roomId);
        JsonNode report = apiGet(admin, "/api/v1/interviews/" + interviewId + "/report").expect(200).json;
        assertThat(report.get("status").isNull()).isTrue();

        // ---- 보고서 다시 만들기도 409 LIVE_DISABLED 이고 작업이 생기지 않는다
        assertThat(api(HttpMethod.POST, interviewer, "/api/v1/interviews/" + interviewId + "/report/regenerate", null).expect(409)
                .json.get("code").asText()).isEqualTo("LIVE_DISABLED");
        for (JsonNode job : apiGet(admin, "/api/v1/interviews/" + interviewId + "/jobs").expect(200).json.get("items")) {
            assertThat(job.get("type").asText()).isEqualTo("BRIEFING");
        }

        // ---- 대시보드: 평가 대기에는 들어가지만, 보고서를 만들지 않는 면접이므로 보고서 대기에는 들어가지 않는다
        JsonNode dashboard = apiGet(admin, "/api/v1/dashboard/summary?recruitmentId=" + recruitmentId).expect(200).json;
        assertThat(dashboard.get("pendingEvaluationCount").asInt()).isEqualTo(1);
        assertThat(dashboard.get("pendingReportCount").asInt()).isZero();
        assertThat(dashboard.get("pendingReports")).isEmpty();
    }

    @Test
    void experiencedInterviewKeepsLiveFeatures() throws Exception {
        final String admin = login("E1001");
        String jobTitle = "실시간검증직무";
        long recruitmentId = newRecruitment(admin, Track.EXPERIENCED, jobTitle);
        Candidate candidate = newCandidate(recruitmentId, Track.EXPERIENCED, "LIVE-ON-1", jobTitle, null);
        final long interviewId = newInterview(admin, candidate.getId(), "JOB", "E1002");
        assertThat(apiGet(admin, "/api/v1/interviews/" + interviewId).expect(200).json.get("liveEnabled").asBoolean()).isTrue();

        fakeAgent.clearRecorded();
        generateBriefing(admin, interviewId);
        AgentDtos.BriefingRequest sent = briefingRequestOf(jobTitle);
        assertThat(sent.getPredictedFollowUps()).isNull();
        assertThat(sent.getApplicantType()).isNull();
        for (JsonNode q : apiGet(admin, "/api/v1/interviews/" + interviewId + "/questions").expect(200).json.get("items")) {
            assertThat(q.get("source").asText()).isNotEqualTo("FOLLOW_UP");
        }

        long roomId = api(HttpMethod.POST, admin, "/api/v1/interviews/" + interviewId + "/room", null).expect(200).json.get("id")
                .asLong();
        api(HttpMethod.POST, admin, "/api/v1/rooms/" + roomId + "/ticket", "{\"audio\":true}").expect(200);

        // 방이 끝났는데 보고서가 없으면 대시보드의 보고서 대기에 들어가고, 보고서 다시 만들기는 접수된다(202).
        api(HttpMethod.PATCH, admin, "/api/v1/interviews/" + interviewId, "{\"status\":\"EVALUATING\"}").expect(200);
        JsonNode dashboard = apiGet(admin, "/api/v1/dashboard/summary?recruitmentId=" + recruitmentId).expect(200).json;
        assertThat(dashboard.get("pendingReportCount").asInt()).isEqualTo(1);
        assertThat(dashboard.get("pendingReports").get(0).get("id").asLong()).isEqualTo(interviewId);
        final long reportJobId = api(HttpMethod.POST, admin, "/api/v1/interviews/" + interviewId + "/report/regenerate", null)
                .expect(202).json.get("jobId").asLong();
        await("보고서 작업", () -> {
            String status = apiGet(admin, "/api/v1/ai-jobs/" + reportJobId).json.path("status").asText();
            return "SUCCEEDED".equals(status) || "FAILED".equals(status);
        });
    }

    @Test
    void aiEvaluationIsAdminOnlyByDefault() throws Exception {
        String admin = login("E1001");
        String jobTitle = "평가권한직무";
        long recruitmentId = newRecruitment(admin, Track.EXPERIENCED, jobTitle);
        Candidate candidate = newCandidate(recruitmentId, Track.EXPERIENCED, "AI-VIS-1", jobTitle, null);
        long interviewId = newInterview(admin, candidate.getId(), "JOB", "E1002");
        String url = "/api/v1/interviews/" + interviewId + "/ai-evaluation";

        // 기본(ADMIN): 배정된 면접관도 403, 관리자는 본다.
        String interviewer = login("E1002");
        assertThat(apiGet(interviewer, url).expect(403).json.get("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(apiGet(admin, url).expect(200).json.get("scores")).isEmpty();

        // app.evaluation.ai-visible-to=ALL 이면 면접관도 본다.
        evaluationProps.setAiVisibleTo(EvaluationProperties.Audience.ALL);
        apiGet(interviewer, url).expect(200);
    }

    @Test
    void seedMatchesTheDefaultSettings() throws Exception {
        String admin = login("E1001");

        // 신입 시드: 끝난 면접의 방은 ENDED 지만 전사·보고서·AI 작업(평가, 보고서)이 없고, 준비된 질문과 예상 꼬리질문은 있다.
        long newGradId = newGradRecruitmentId(admin);
        long candidateId = apiGet(admin, "/api/v1/recruitments/" + newGradId + "/candidates?q=2026H-9001").expect(200).json
                .get("items").get(0).get("id").asLong();
        JsonNode done = apiGet(admin, "/api/v1/candidates/" + candidateId).expect(200).json.get("interviews").get(0);
        assertThat(done.get("status").asText()).isEqualTo("DONE");
        assertThat(done.get("roomStatus").asText()).isEqualTo("ENDED");
        assertThat(done.get("liveEnabled").asBoolean()).isFalse();
        assertThat(done.get("reportStatus").isNull()).isTrue();
        assertThat(apiGet(admin, "/api/v1/rooms/" + done.get("roomId").asLong() + "/utterances").expect(200).json.get("items"))
                .isEmpty();
        assertThat(apiGet(admin, "/api/v1/rooms/" + done.get("roomId").asLong() + "/follow-ups").expect(200).json.get("items"))
                .isEmpty();
        for (JsonNode job : apiGet(admin, "/api/v1/interviews/" + done.get("id").asLong() + "/jobs").expect(200).json.get("items")) {
            assertThat(job.get("type").asText()).isEqualTo("BRIEFING");
        }
        int predicted = 0;
        for (JsonNode q : apiGet(admin, "/api/v1/interviews/" + done.get("id").asLong() + "/questions").expect(200).json.get("items")) {
            if ("예상 꼬리질문".equals(q.path("category").asText())) {
                predicted++;
            }
        }
        assertThat(predicted).isGreaterThan(0);
        // 신입 채용에는 진행 중(LIVE)인 방이 없다.
        assertThat(apiGet(admin, "/api/v1/interviews?recruitmentId=" + newGradId + "&status=IN_PROGRESS").expect(200).json.get("total")
                .asInt()).isZero();

        // 경력 시드: 진행 중인 방과 보고서가 있어 관리자 집계 화면에 볼거리가 있다.
        long experiencedId = apiGet(admin, "/api/v1/recruitments?track=EXPERIENCED").expect(200).json.get("items").get(0).get("id")
                .asLong();
        assertThat(apiGet(admin, "/api/v1/recruitments/" + experiencedId).expect(200).json.get("candidateCount").asInt())
                .isGreaterThanOrEqualTo(12);
        assertThat(apiGet(admin, "/api/v1/admin/operations?recruitmentId=" + experiencedId).expect(200).json.get("summary").get("live")
                .asInt()).isGreaterThanOrEqualTo(2);
        JsonNode quality = apiGet(admin, "/api/v1/admin/quality?recruitmentId=" + experiencedId).expect(200).json;
        assertThat(quality.get("reports").get("liveCount").asInt()).isGreaterThan(0);
        assertThat(quality.get("followUps").get("suggested").asInt()).isGreaterThan(0);
        assertThat(apiGet(admin, "/api/v1/admin/interviewer-stats?recruitmentId=" + experiencedId).expect(200).json.get("items").size())
                .isGreaterThan(0);
    }

    private AgentDtos.BriefingRequest briefingRequestOf(String jobTitle) {
        AgentDtos.BriefingRequest found = null;
        for (Object request : fakeAgent.getRecorded()) {
            if (request instanceof AgentDtos.BriefingRequest && jobTitle.equals(((AgentDtos.BriefingRequest) request).getJobTitle())) {
                found = (AgentDtos.BriefingRequest) request;
            }
        }
        assertThat(found).as("Fake Agent 가 받은 브리핑 요청").isNotNull();
        return found;
    }

    private static JsonNode parentOf(JsonNode questions, long id) {
        for (JsonNode q : questions) {
            if (q.get("id").asLong() == id && !"FOLLOW_UP".equals(q.get("source").asText())) {
                return q;
            }
        }
        return null;
    }
}
