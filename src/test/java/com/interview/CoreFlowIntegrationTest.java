package com.interview;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.agent.FakeAgentClient;
import com.interview.auth.AppSecurityProperties;
import com.interview.support.ApiTestSupport;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * 핵심 흐름을 처음부터 끝까지 한 번에 검증한다(계약 6장):
 * 로그인 → 업로드(성공·오류 행) → 자동 매칭 → 브리핑 작업(Fake Agent) → 방 생성/시작/입장권
 * → 내부 콜백으로 전사 저장 → 종료 → AI 평가·보고서(LIVE) → refined 콜백 → 보고서 v2(REFINED) → 평가 제출 → DONE
 */
class CoreFlowIntegrationTest extends ApiTestSupport {

    @Autowired
    private FakeAgentClient fakeAgent;

    @Autowired
    private AppSecurityProperties securityProps;

    @Test
    void fullFlow() throws Exception {
        // ---- 로그인 ----
        final String admin = login("E1001");
        assertThat(apiGet(admin, "/api/v1/me").expect(200).json.get("role").asText()).isEqualTo("ADMIN");
        Long recruitmentId = newGradRecruitmentId(admin);

        // ---- 업로드: 샘플 50행 = 성공 47 + 오류 3 ----
        byte[] sample = download(admin, "/api/v1/uploads/template");
        JsonNode batch = upload(admin, recruitmentId, "2026하반기_면접대상자.xlsx", sample).expect(200).json;
        assertThat(batch.get("fileName").asText()).isEqualTo("2026하반기_면접대상자.xlsx");
        assertThat(batch.get("uploadedBy").asText()).isEqualTo("김서연");
        assertThat(batch.get("totalRows").asInt()).isEqualTo(50);
        assertThat(batch.get("successRows").asInt()).isEqualTo(47);
        assertThat(batch.get("failedRows").asInt()).isEqualTo(3);
        Map<Integer, String> errorByRow = new HashMap<Integer, String>();
        for (JsonNode e : batch.get("errors")) {
            errorByRow.put(e.get("rowNumber").asInt(), e.get("code").asText());
            assertThat(e.get("message").asText()).isNotEmpty();
        }
        assertThat(errorByRow).containsEntry(16, "REQUIRED_MISSING").containsEntry(29, "UNKNOWN_JOB_CODE")
                .containsEntry(42, "DUPLICATE_IN_FILE").hasSize(3);
        JsonNode saved = apiGet(admin, "/api/v1/uploads/" + batch.get("id").asLong()).expect(200).json;
        assertThat(saved.get("errors")).hasSize(3);
        assertThat(apiGet(admin, "/api/v1/recruitments/" + recruitmentId + "/uploads").expect(200).json.get("items").size())
                .isGreaterThanOrEqualTo(1);

        // 같은 파일을 다시 올리면 새로 만들지 않고 갱신한다(면접자 수가 그대로).
        long before = apiGet(admin, "/api/v1/recruitments/" + recruitmentId).expect(200).json.get("candidateCount").asLong();
        upload(admin, recruitmentId, "again.xlsx", sample).expect(200);
        long after = apiGet(admin, "/api/v1/recruitments/" + recruitmentId).expect(200).json.get("candidateCount").asLong();
        assertThat(after).isEqualTo(before);

        // ---- 목록 ----
        JsonNode list = apiGet(admin, "/api/v1/recruitments/" + recruitmentId + "/candidates?q=2026H-00001").expect(200).json;
        assertThat(list.get("total").asInt()).isEqualTo(1);
        final long candidateId = list.get("items").get(0).get("id").asLong();
        assertThat(apiGet(admin, "/api/v1/recruitments/" + recruitmentId + "/candidates?jobCode=SW&size=5").expect(200).json
                .get("items").size()).isEqualTo(5);

        // ---- 자동 매칭: 면접과 면접관 배정이 생긴다 ----
        JsonNode match = api(HttpMethod.POST, admin, "/api/v1/recruitments/" + recruitmentId + "/assignments/auto", null)
                .expect(200).json;
        assertThat(match.get("createdInterviewCount").asInt()).isGreaterThanOrEqualTo(94);
        assertThat(match.get("unassignedInterviewCount").asInt()).isZero();
        JsonNode detail = apiGet(admin, "/api/v1/candidates/" + candidateId).expect(200).json;
        assertThat(detail.get("interviews")).hasSize(2);
        JsonNode pt = detail.get("interviews").get(0);
        assertThat(pt.get("type").asText()).isEqualTo("PT");
        assertThat(pt.get("status").asText()).isEqualTo("WAITING");
        assertThat(pt.get("interviewers")).hasSize(2);
        assertThat(pt.get("interviewers").get(0).get("matchedBy").asText()).isEqualTo("JD");
        final long interviewId = pt.get("id").asLong();
        // 목록의 type 은 "지금 차수"(여기서는 PT) 면접의 종류로 거른다.
        String byNo = "/api/v1/recruitments/" + recruitmentId + "/candidates?q=2026H-00001&type=";
        assertThat(apiGet(admin, byNo + "PT").expect(200).json.get("total").asInt()).isEqualTo(1);
        assertThat(apiGet(admin, byNo + "PERSONALITY").expect(200).json.get("total").asInt()).isZero();
        // 다시 실행해도 이미 배정된 면접은 건드리지 않는다.
        JsonNode rematch = api(HttpMethod.POST, admin, "/api/v1/recruitments/" + recruitmentId + "/assignments/auto", null)
                .expect(200).json;
        assertThat(rematch.get("createdInterviewCount").asInt()).isZero();
        assertThat(rematch.get("assignedInterviewCount").asInt()).isZero();

        // ---- 브리핑 작업(Fake Agent) ----
        await("브리핑", () -> "SUCCEEDED".equals(
                apiGet(admin, "/api/v1/interviews/" + interviewId + "/briefing").json.path("status").asText()));
        JsonNode briefing = apiGet(admin, "/api/v1/interviews/" + interviewId + "/briefing").expect(200).json;
        assertThat(briefing.get("model").asText()).isEqualTo("fake-llm");
        assertThat(briefing.get("summary").get("headline").asText()).isNotEmpty();
        assertThat(briefing.get("excluded").toString()).contains("gender").contains("family");
        // 수동 재생성은 202 와 jobId. 아직 묻지 않은 생성 질문은 새 질문으로 바뀐다.
        JsonNode regenerate = api(HttpMethod.POST, admin, "/api/v1/interviews/" + interviewId + "/briefing/generate", null)
                .expect(202).json;
        final long briefingJobId = regenerate.get("jobId").asLong();
        await("브리핑 재생성", () -> "SUCCEEDED".equals(apiGet(admin, "/api/v1/ai-jobs/" + briefingJobId).json.path("status").asText()));
        JsonNode questions = apiGet(admin, "/api/v1/interviews/" + interviewId + "/questions").expect(200).json.get("items");
        assertThat(questions).hasSize(12);
        final long firstQuestionId = questions.get(0).get("id").asLong();
        // 수동 질문 추가
        JsonNode manual = api(HttpMethod.POST, admin, "/api/v1/interviews/" + interviewId + "/questions",
                "{\"text\":\"직접 추가한 질문\",\"category\":\"기타\"}").expect(200).json;
        assertThat(manual.get("source").asText()).isEqualTo("MANUAL");

        // ---- 일정 지정 → SCHEDULED ----
        JsonNode patched = api(HttpMethod.PATCH, admin, "/api/v1/interviews/" + interviewId,
                "{\"scheduledAt\":\"2026-11-20T14:00:00+09:00\"}").expect(200).json;
        assertThat(patched.get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(patched.get("scheduledAt").asText()).isEqualTo("2026-11-20T14:00:00+09:00");

        // ---- 방 생성 / 시작 / 입장권 ----
        JsonNode room = api(HttpMethod.POST, admin, "/api/v1/interviews/" + interviewId + "/room", null).expect(200).json;
        final long roomId = room.get("id").asLong();
        assertThat(room.get("status").asText()).isEqualTo("CREATED");
        assertThat(api(HttpMethod.POST, admin, "/api/v1/interviews/" + interviewId + "/room", null).expect(200).json.get("id")
                .asLong()).isEqualTo(roomId);
        api(HttpMethod.POST, admin, "/api/v1/rooms/" + roomId + "/end", null).expect(409);
        assertThat(api(HttpMethod.POST, admin, "/api/v1/rooms/" + roomId + "/start", null).expect(200).json.get("status")
                .asText()).isEqualTo("LIVE");
        assertThat(api(HttpMethod.POST, admin, "/api/v1/rooms/" + roomId + "/start", null).expect(409).json.get("code")
                .asText()).isEqualTo("ROOM_ALREADY_LIVE");
        assertThat(apiGet(admin, "/api/v1/interviews/" + interviewId).expect(200).json.get("status").asText())
                .isEqualTo("IN_PROGRESS");

        JsonNode ticket = api(HttpMethod.POST, admin, "/api/v1/rooms/" + roomId + "/ticket", "{\"audio\":true}").expect(200).json;
        assertThat(ticket.get("expiresIn").asInt()).isEqualTo(300);
        assertThat(ticket.get("agentWsUrl").asText()).isEqualTo("ws://localhost:8000/ws/v1/rooms/" + roomId);
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(securityProps.getRoomTicketSecret().getBytes(StandardCharsets.UTF_8)))
                .build().parseClaimsJws(ticket.get("ticket").asText()).getBody();
        assertThat(claims.get("roomId", Number.class).longValue()).isEqualTo(roomId);
        assertThat(claims.get("audio", Boolean.class)).isTrue();
        assertThat(claims.get("name", String.class)).isEqualTo("김서연");
        assertThat(claims.getSubject()).isNotEmpty();
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(300000L);

        // ---- Agent 콜백: context, 전사, 꼬리질문 ----
        JsonNode context = internal(HttpMethod.GET, "/internal/v1/rooms/" + roomId + "/context", null).expect(200).json;
        assertThat(context.get("status").asText()).isEqualTo("LIVE");
        assertThat(context.get("interviewId").asLong()).isEqualTo(interviewId);
        assertThat(context.get("jobTitle").asText()).isEqualTo("SW개발");
        assertThat(context.get("candidateBrief").asText()).isNotEmpty();
        assertThat(context.get("questions").size()).isGreaterThanOrEqualTo(10);
        assertThat(context.get("questionBank").size()).isGreaterThan(0);

        String utterances = "{\"items\":["
                + utterance(1, "INTERVIEWER", "QUESTION_MATCH", "첫 질문입니다", firstQuestionId) + ","
                + utterance(2, "CANDIDATE", "LLM", "어 첫 답변입니다", null) + ","
                + utterance(3, "INTERVIEWER", "LLM", "둘째 질문입니다", null) + ","
                + utterance(4, "CANDIDATE", "LLM", "둘째 답변입니다", null) + "]}";
        assertThat(internal(HttpMethod.POST, "/internal/v1/rooms/" + roomId + "/utterances", utterances).expect(200).json
                .get("saved").asInt()).isEqualTo(4);
        // 같은 (roomId, seq)는 덮어쓴다.
        internal(HttpMethod.POST, "/internal/v1/rooms/" + roomId + "/utterances",
                "{\"items\":[" + utterance(2, "CANDIDATE", "MANUAL", "고친 첫 답변입니다", null) + "]}").expect(200);
        JsonNode stored = apiGet(admin, "/api/v1/rooms/" + roomId + "/utterances").expect(200).json.get("items");
        assertThat(stored).hasSize(4);
        assertThat(stored.get(1).get("refinedText").asText()).isEqualTo("고친 첫 답변입니다");
        assertThat(stored.get(1).get("speakerMethod").asText()).isEqualTo("MANUAL");
        assertThat(stored.get(1).get("source").asText()).isEqualTo("LIVE");
        assertThat(apiGet(admin, "/api/v1/rooms/" + roomId + "/utterances?afterSeq=2").expect(200).json.get("items")).hasSize(2);
        assertThat(apiGet(admin, "/api/v1/rooms/" + roomId).expect(200).json.get("lastSeq").asInt()).isEqualTo(4);
        // 전사에 걸린 질문은 "물어봄"이 된다.
        JsonNode askedQuestion = apiGet(admin, "/api/v1/interviews/" + interviewId + "/questions").expect(200).json.get("items");
        boolean asked = false;
        for (JsonNode q : askedQuestion) {
            if (q.get("id").asLong() == firstQuestionId) {
                asked = q.get("asked").asBoolean() && q.get("askedAtSeq").asInt() == 1;
            }
        }
        assertThat(asked).isTrue();

        internal(HttpMethod.POST, "/internal/v1/rooms/" + roomId + "/follow-ups",
                "{\"basedOnSeq\":2,\"items\":[{\"clientId\":\"f-2-1\",\"text\":\"그때 가장 어려웠던 점은?\",\"rationale\":\"구체화\"}]}")
                        .expect(200);
        JsonNode followUps = apiGet(admin, "/api/v1/rooms/" + roomId + "/follow-ups").expect(200).json.get("items");
        assertThat(followUps).hasSize(1);
        JsonNode used = api(HttpMethod.POST, admin, "/api/v1/follow-ups/" + followUps.get(0).get("id").asLong() + "/use", null)
                .expect(200).json;
        assertThat(used.get("source").asText()).isEqualTo("FOLLOW_UP");
        assertThat(used.get("parentQuestionId").asLong()).isEqualTo(firstQuestionId);

        // 면접에서 쓴 질문을 은행에 담는다: 직무·유형은 면접에서 오고, 같은 글은 한 번만 들어간다.
        String toBank = "/api/v1/question-bank/from-question/" + used.get("id").asLong();
        JsonNode banked = api(HttpMethod.POST, admin, toBank, null).expect(200).json;
        assertThat(banked.get("jobCode").asText()).isEqualTo("SW");
        assertThat(banked.get("interviewType").asText()).isEqualTo("PT");
        assertThat(banked.get("category").asText()).isEqualTo("꼬리질문");
        assertThat(banked.get("text").asText()).isEqualTo("그때 가장 어려웠던 점은?");
        assertThat(api(HttpMethod.POST, admin, toBank, null).expect(200).json.get("id").asLong())
                .isEqualTo(banked.get("id").asLong());
        JsonNode reworded = api(HttpMethod.POST, admin, toBank,
                "{\"text\":\"프로젝트에서 가장 어려웠던 점은 무엇이었나요?\",\"category\":\"문제 해결\"}").expect(200).json;
        assertThat(reworded.get("id").asLong()).isNotEqualTo(banked.get("id").asLong());
        assertThat(reworded.get("category").asText()).isEqualTo("문제 해결");
        api(HttpMethod.POST, admin, "/api/v1/question-bank/from-question/999999999", null).expect(404);
        internal(HttpMethod.POST, "/internal/v1/rooms/" + roomId + "/events",
                "{\"type\":\"AUDIO_DROPPED\",\"message\":\"테스트\",\"data\":{\"ms\":120}}").expect(200);

        // ---- 종료 → finalize → AI 평가 → 보고서(LIVE, BRIEF) ----
        fakeAgent.clearRecorded();
        assertThat(api(HttpMethod.POST, admin, "/api/v1/rooms/" + roomId + "/end", null).expect(200).json.get("status").asText())
                .isEqualTo("ENDED");
        assertThat(apiGet(admin, "/api/v1/interviews/" + interviewId).expect(200).json.get("status").asText())
                .isEqualTo("EVALUATING");
        api(HttpMethod.POST, admin, "/api/v1/rooms/" + roomId + "/ticket", "{}").expect(409);
        await("보고서 v1", () -> apiGet(admin, "/api/v1/interviews/" + interviewId + "/report").json.path("version").asInt() == 1);
        assertThat(fakeAgent.getFinalizedRooms()).contains(roomId);
        JsonNode report1 = apiGet(admin, "/api/v1/interviews/" + interviewId + "/report").expect(200).json;
        assertThat(report1.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(report1.get("basis").asText()).isEqualTo("LIVE");
        assertThat(report1.get("detail").asText()).isEqualTo("BRIEF");
        assertThat(report1.get("content").get("headline").asText()).isNotEmpty();
        assertThat(report1.get("content").get("competencies")).isEmpty();
        JsonNode aiEvaluation = apiGet(admin, "/api/v1/interviews/" + interviewId + "/ai-evaluation").expect(200).json;
        assertThat(aiEvaluation.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(aiEvaluation.get("scores")).hasSize(4);
        assertThat(aiEvaluation.get("scores").get(0).get("criterionName").asText()).isNotEmpty();
        assertThat(aiEvaluation.get("scores").get(0).get("maxScore").asInt()).isEqualTo(5);
        Set<String> jobTypes = new HashSet<String>();
        for (JsonNode job : apiGet(admin, "/api/v1/interviews/" + interviewId + "/jobs").expect(200).json.get("items")) {
            if ("SUCCEEDED".equals(job.get("status").asText())) {
                jobTypes.add(job.get("type").asText());
            }
        }
        assertThat(jobTypes).contains("BRIEFING", "AI_EVALUATION", "REPORT");
        JsonNode interviewAfterReport = apiGet(admin, "/api/v1/interviews/" + interviewId).expect(200).json;
        assertThat(interviewAfterReport.get("reportStatus").asText()).isEqualTo("SUCCEEDED");
        assertThat(interviewAfterReport.get("reportBasis").asText()).isEqualTo("LIVE");
        assertThat(interviewAfterReport.get("roomStatus").asText()).isEqualTo("ENDED");

        // ---- refined 콜백 → 전사 교체(POST) → 보고서 v2(REFINED) ----
        String refined = "{\"sttModel\":\"whisper-large-v3\",\"items\":["
                + utterance(1, "INTERVIEWER", "QUESTION_MATCH", "보정된 첫 질문입니다", firstQuestionId) + ","
                + utterance(2, "CANDIDATE", "LLM", "보정된 첫 답변입니다", null) + ","
                + utterance(3, "CANDIDATE", "LLM", "보정된 둘째 답변입니다", null) + "]}";
        JsonNode refinedResult = internal(HttpMethod.PUT, "/internal/v1/rooms/" + roomId + "/refined-transcript", refined)
                .expect(200).json;
        assertThat(refinedResult.get("jobId").asLong()).isPositive();
        await("보고서 v2", () -> apiGet(admin, "/api/v1/interviews/" + interviewId + "/report").json.path("version").asInt() == 2);
        JsonNode report2 = apiGet(admin, "/api/v1/interviews/" + interviewId + "/report").expect(200).json;
        assertThat(report2.get("basis").asText()).isEqualTo("REFINED");
        assertThat(report2.get("content").get("overallComment").asText()).contains("전사 3개");
        JsonNode versions = apiGet(admin, "/api/v1/interviews/" + interviewId + "/report/versions").expect(200).json.get("items");
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).get("version").asInt()).isEqualTo(2);
        assertThat(versions.get(1).get("basis").asText()).isEqualTo("LIVE");
        JsonNode refinedStored = apiGet(admin, "/api/v1/rooms/" + roomId + "/utterances").expect(200).json.get("items");
        assertThat(refinedStored).hasSize(3);
        for (JsonNode u : refinedStored) {
            assertThat(u.get("source").asText()).isEqualTo("POST");
        }
        assertThat(apiGet(admin, "/api/v1/interviews/" + interviewId).expect(200).json.get("reportBasis").asText())
                .isEqualTo("REFINED");

        // ---- 면접관 평가: 배정된 두 명이 모두 제출하면 DONE ----
        assertThat(apiGet(admin, "/api/v1/interviews/" + interviewId + "/criteria").expect(200).json.get("items")).hasSize(4);
        Map<Long, String> employeeNoById = new HashMap<Long, String>();
        for (JsonNode u : apiGet(admin, "/api/v1/interviewers").expect(200).json.get("items")) {
            employeeNoById.put(u.get("id").asLong(), u.get("employeeNo").asText());
        }
        String evaluation = "{\"memo\":\"측정 방법을 잘 설명함\",\"scores\":[{\"criterionCode\":\"PROBLEM_SOLVING\",\"score\":4,"
                + "\"comment\":\"좋음\"}],\"grade\":\"A\",\"submitted\":true}";
        int submitted = 0;
        for (JsonNode interviewer : pt.get("interviewers")) {
            String token = login(employeeNoById.get(interviewer.get("id").asLong()));
            // 자동 저장(제출 전)
            api(HttpMethod.PUT, token, "/api/v1/interviews/" + interviewId + "/my-evaluation", "{\"memo\":\"작성 중\"}").expect(200);
            JsonNode mine = api(HttpMethod.PUT, token, "/api/v1/interviews/" + interviewId + "/my-evaluation", evaluation)
                    .expect(200).json;
            assertThat(mine.get("submitted").asBoolean()).isTrue();
            assertThat(apiGet(token, "/api/v1/interviews/" + interviewId + "/my-evaluation").expect(200).json.get("memo")
                    .asText()).isEqualTo("측정 방법을 잘 설명함");
            submitted++;
            String expected = submitted == 2 ? "DONE" : "EVALUATING";
            assertThat(apiGet(admin, "/api/v1/interviews/" + interviewId).expect(200).json.get("status").asText())
                    .isEqualTo(expected);
        }
        // 잘못된 평가 항목·점수는 400
        api(HttpMethod.PUT, admin, "/api/v1/interviews/" + interviewId + "/my-evaluation",
                "{\"scores\":[{\"criterionCode\":\"NOPE\",\"score\":1}]}").expect(400);
        api(HttpMethod.PUT, admin, "/api/v1/interviews/" + interviewId + "/my-evaluation",
                "{\"scores\":[{\"criterionCode\":\"LOGIC\",\"score\":9}]}").expect(400);

        // ---- 진행 표시와 대시보드 ----
        JsonNode progress = apiGet(admin, "/api/v1/candidates/" + candidateId + "/progress").expect(200).json;
        assertThat(progress.get("items")).hasSize(2);
        assertThat(progress.get("items").get(0).get("status").asText()).isEqualTo("DONE");
        assertThat(progress.get("currentStage").get("type").asText()).isEqualTo("PERSONALITY");
        JsonNode dashboard = apiGet(admin, "/api/v1/dashboard/summary?recruitmentId=" + recruitmentId).expect(200).json;
        assertThat(dashboard.get("todayCount").asInt()).isGreaterThan(0);
        assertThat(dashboard.get("todayInterviews").size()).isGreaterThan(0);
    }

    private static String utterance(int seq, String speaker, String method, String text, Long matchedQuestionId) {
        return "{\"seq\":" + seq + ",\"speaker\":\"" + speaker + "\",\"speakerMethod\":\"" + method + "\",\"rawText\":\"" + text
                + "\",\"refinedText\":\"" + text + "\",\"startMs\":" + (seq * 1000) + ",\"endMs\":" + (seq * 1000 + 900)
                + ",\"matchedQuestionId\":" + matchedQuestionId + "}";
    }
}
