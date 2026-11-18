package com.interview.persona;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.agent.FakeAgentClient;
import com.interview.agent.dto.AgentDtos;
import com.interview.candidate.Candidate;
import com.interview.recruitment.Track;
import com.interview.support.FixtureTestSupport;

/**
 * 면접 페르소나(API 명세 2.12)와 면접에 고르기(2.5 의 PUT persona).
 * 페르소나 표는 모든 테스트가 함께 쓰므로, 테스트가 끝나면 테스트 전의 상태로 되돌린다.
 */
class PersonaApiTest extends FixtureTestSupport {

    @Autowired
    private PersonaRepository personaRepository;

    @Autowired
    private FakeAgentClient fakeAgent;

    private String admin;

    /** 테스트 전의 페르소나 상태: id → { enabled, recommended }. */
    private final Map<Long, boolean[]> before = new HashMap<Long, boolean[]>();

    @BeforeEach
    void setUp() throws Exception {
        admin = login("E1001");
        before.clear();
        for (Persona p : personaRepository.findAllByOrderByIdAsc()) {
            before.put(p.getId(), new boolean[] { p.isEnabled(), p.isRecommended() });
        }
    }

    /** 테스트가 만든 페르소나는 지우고, 원래 있던 것은 쓰는지·추천인지를 테스트 전의 값으로 되돌린다. */
    @AfterEach
    void restoreBefore() {
        fakeAgent.holdBriefingsOf(null, null);
        for (Persona p : personaRepository.findAllByOrderByIdAsc()) {
            boolean[] state = before.get(p.getId());
            if (state == null) {
                personaRepository.delete(p);
            } else {
                p.setEnabled(state[0]);
                p.setRecommended(state[1]);
                personaRepository.save(p);
            }
        }
    }

    @Test
    void defaultsAreSeededInOrder() throws Exception {
        JsonNode items = apiGet(login("E1002"), "/api/v1/personas").expect(200).json.get("items");
        assertThat(items).hasSize(5);
        String[] names = { "기술 심층형", "압박 검증형", "경험 행동형", "표준 구조화형", "편안한 대화형" };
        for (int n = 0; n < names.length; n++) {
            JsonNode p = items.get(n);
            assertThat(p.get("name").asText()).isEqualTo(names[n]);
            assertThat(p.get("recommended").asBoolean()).isEqualTo(n < 3);
            assertThat(p.get("enabled").asBoolean()).isTrue();
            assertThat(p.get("custom").asBoolean()).isFalse();
            assertThat(p.get("description").asText()).isNotEmpty();
            assertThat(p.get("questionType").asText()).isNotEmpty();
            assertThat(p.get("followUpType").asText()).isNotEmpty();
            assertThat(p.get("fit").asText()).isNotEmpty();
            assertThat(p.get("guard").asText()).isNotEmpty();
        }
        assertThat(items.get(2).get("tone").asText()).isEqualTo("ok");
        assertThat(items.get(2).get("guard").asText()).isEqualTo("가정이 아니라 실제로 있었던 일만 묻습니다.");
    }

    @Test
    void adminManagesPersonasByTheRules() throws Exception {
        // 관리자만 만들 수 있다.
        api(HttpMethod.POST, login("E1002"), "/api/v1/admin/personas", "{\"name\":\"면접관이 만든 것\"}").expect(403);

        // 만들기: enabled true, recommended false, custom true, tone 기본값 neutral
        assertThat(api(HttpMethod.POST, admin, "/api/v1/admin/personas", "{\"description\":\"이름 없음\"}").expect(400).json.get("code")
                .asText()).isEqualTo("PERSONA_NAME_REQUIRED");
        assertThat(api(HttpMethod.POST, admin, "/api/v1/admin/personas", "{\"name\":\"  \"}").expect(400).json.get("code").asText())
                .isEqualTo("PERSONA_NAME_REQUIRED");
        JsonNode created = api(HttpMethod.POST, admin, "/api/v1/admin/personas", "{\"name\":\"사례 검증형\",\"description\":\"사례를 끝까지 확인합니다.\","
                + "\"questionType\":\"사례 질문\",\"followUpType\":\"세부 확인\",\"recommended\":true,\"enabled\":false}").expect(200).json;
        long customId = created.get("id").asLong();
        assertThat(created.get("enabled").asBoolean()).isTrue();
        assertThat(created.get("recommended").asBoolean()).isFalse();
        assertThat(created.get("custom").asBoolean()).isTrue();
        assertThat(created.get("tone").asText()).isEqualTo("neutral");
        assertThat(created.get("fit").isNull()).isTrue();
        JsonNode list = apiGet(admin, "/api/v1/personas").expect(200).json.get("items");
        assertThat(list.get(list.size() - 1).get("id").asLong()).isEqualTo(customId); // 만든 순서

        // 이름 중복, 모르는 tone
        assertThat(api(HttpMethod.POST, admin, "/api/v1/admin/personas", "{\"name\":\"사례 검증형\"}").expect(409).json.get("code")
                .asText()).isEqualTo("PERSONA_NAME_TAKEN");
        assertThat(api(HttpMethod.PATCH, admin, "/api/v1/admin/personas/" + customId, "{\"name\":\"기술 심층형\"}").expect(409).json
                .get("code").asText()).isEqualTo("PERSONA_NAME_TAKEN");
        api(HttpMethod.POST, admin, "/api/v1/admin/personas", "{\"name\":\"색이 이상함\",\"tone\":\"purple\"}").expect(400);

        // 고치기: 보낸 것만 바뀐다. 자기 이름을 그대로 보내는 것은 중복이 아니다.
        JsonNode patched = api(HttpMethod.PATCH, admin, "/api/v1/admin/personas/" + customId,
                "{\"name\":\"사례 검증형\",\"tone\":\"warn\",\"guard\":\"없는 사례를 지어내게 하지 않습니다.\"}").expect(200).json;
        assertThat(patched.get("tone").asText()).isEqualTo("warn");
        assertThat(patched.get("guard").asText()).isEqualTo("없는 사례를 지어내게 하지 않습니다.");
        assertThat(patched.get("description").asText()).isEqualTo("사례를 끝까지 확인합니다.");
        assertThat(api(HttpMethod.PATCH, admin, "/api/v1/admin/personas/" + customId, "{\"name\":\"\"}").expect(400).json.get("code")
                .asText()).isEqualTo("PERSONA_NAME_REQUIRED");

        // 추천 ↔ 사용 연동
        JsonNode recommended = api(HttpMethod.PATCH, admin, "/api/v1/admin/personas/" + customId, "{\"recommended\":true}")
                .expect(200).json;
        assertThat(recommended.get("recommended").asBoolean()).isTrue();
        JsonNode disabled = api(HttpMethod.PATCH, admin, "/api/v1/admin/personas/" + customId, "{\"enabled\":false}").expect(200).json;
        assertThat(disabled.get("enabled").asBoolean()).isFalse();
        assertThat(disabled.get("recommended").asBoolean()).isFalse();
        JsonNode again = api(HttpMethod.PATCH, admin, "/api/v1/admin/personas/" + customId, "{\"recommended\":true}").expect(200).json;
        assertThat(again.get("enabled").asBoolean()).isTrue();
        assertThat(again.get("recommended").asBoolean()).isTrue();
        api(HttpMethod.PATCH, admin, "/api/v1/admin/personas/" + customId, "{\"recommended\":true,\"enabled\":false}").expect(400);

        // 기본 페르소나는 지울 수 없다. 없는 것은 404.
        long builtInId = list.get(0).get("id").asLong();
        assertThat(api(HttpMethod.DELETE, admin, "/api/v1/admin/personas/" + builtInId, null).expect(409).json.get("code").asText())
                .isEqualTo("PERSONA_BUILT_IN");
        assertThat(api(HttpMethod.DELETE, admin, "/api/v1/admin/personas/99999999", null).expect(404).json.get("code").asText())
                .isEqualTo("PERSONA_NOT_FOUND");
        api(HttpMethod.PATCH, admin, "/api/v1/admin/personas/99999999", "{\"tone\":\"ok\"}").expect(404);

        // 마지막 하나 보호: 직접 만든 것만 남기고 모두 끄면, 그것은 끌 수도 지울 수도 없다.
        for (JsonNode p : list) {
            if (p.get("id").asLong() != customId) {
                api(HttpMethod.PATCH, admin, "/api/v1/admin/personas/" + p.get("id").asLong(), "{\"enabled\":false}").expect(200);
            }
        }
        assertThat(api(HttpMethod.PATCH, admin, "/api/v1/admin/personas/" + customId, "{\"enabled\":false}").expect(409).json
                .get("code").asText()).isEqualTo("PERSONA_LAST_ONE");
        assertThat(api(HttpMethod.DELETE, admin, "/api/v1/admin/personas/" + customId, null).expect(409).json.get("code").asText())
                .isEqualTo("PERSONA_LAST_ONE");
        // 다른 것을 하나 켜면 지울 수 있다.
        api(HttpMethod.PATCH, admin, "/api/v1/admin/personas/" + builtInId, "{\"enabled\":true}").expect(200);
        api(HttpMethod.DELETE, admin, "/api/v1/admin/personas/" + customId, null).expect(204);
        assertThat(apiGet(admin, "/api/v1/personas").expect(200).json.get("items")).hasSize(5);
    }

    @Test
    void interviewerChoosesPersonaForAnInterview() throws Exception {
        String jobTitle = "페르소나검증직무";
        long recruitmentId = newRecruitment(admin, Track.EXPERIENCED, jobTitle);
        Candidate candidate = newCandidate(recruitmentId, Track.EXPERIENCED, "PERSONA-1", jobTitle, null);
        final long interviewId = newInterview(admin, candidate.getId(), "JOB", "E1002");
        String url = "/api/v1/interviews/" + interviewId + "/persona";
        JsonNode list = apiGet(admin, "/api/v1/personas").expect(200).json.get("items");
        long depthId = list.get(0).get("id").asLong();      // 기술 심층형(추천, 기본 선택)
        long starId = list.get(2).get("id").asLong();       // 경험 행동형
        long standardId = list.get(3).get("id").asLong();   // 표준 구조화형

        // 고르지 않았으면 기본 선택: 추천이면서 쓰는 것 가운데 가장 앞선 것
        JsonNode persona = apiGet(admin, "/api/v1/interviews/" + interviewId).expect(200).json.get("persona");
        assertThat(persona.get("id").asLong()).isEqualTo(depthId);
        assertThat(persona.get("name").asText()).isEqualTo("기술 심층형");
        assertThat(persona.get("tone").asText()).isEqualTo("brand");
        assertThat(persona.get("questionType").asText()).isEqualTo("원리·대안 질문");
        assertThat(persona.get("followUpType").asText()).isEqualTo("장단점 심화");

        // 사전 리포트를 먼저 만든다(기본 선택의 페르소나가 Agent 로 간다).
        fakeAgent.clearRecorded();
        generateBriefing(admin, interviewId);
        assertThat(lastBriefingRequest(jobTitle).getPersona().getName()).isEqualTo("기술 심층형");

        // 권한과 입력 검사
        api(HttpMethod.PUT, login("E1005"), url, "{\"personaId\":" + starId + "}").expect(403);
        api(HttpMethod.PUT, admin, url, "{}").expect(400);
        assertThat(api(HttpMethod.PUT, admin, url, "{\"personaId\":99999999}").expect(404).json.get("code").asText())
                .isEqualTo("PERSONA_NOT_FOUND");
        api(HttpMethod.PATCH, admin, "/api/v1/admin/personas/" + standardId, "{\"enabled\":false}").expect(200);
        assertThat(api(HttpMethod.PUT, admin, url, "{\"personaId\":" + standardId + "}").expect(400).json.get("code").asText())
                .isEqualTo("PERSONA_NOT_AVAILABLE");
        api(HttpMethod.PATCH, admin, "/api/v1/admin/personas/" + standardId, "{\"enabled\":true}").expect(200);

        // 기본 선택과 같은 것을 고르면 사전 리포트를 다시 만들지 않는다.
        String interviewer = login("E1002");
        assertThat(api(HttpMethod.PUT, interviewer, url, "{\"personaId\":" + depthId + "}").expect(200).json.get("persona").get("id")
                .asLong()).isEqualTo(depthId);
        assertThat(briefingJobCount(interviewId)).isEqualTo(1);

        // 다른 것을 고르면 응답은 Interview 이고, 사전 리포트를 새 페르소나로 다시 만든다.
        JsonNode chosen = api(HttpMethod.PUT, interviewer, url, "{\"personaId\":" + starId + "}").expect(200).json;
        assertThat(chosen.get("id").asLong()).isEqualTo(interviewId);
        assertThat(chosen.get("persona").get("name").asText()).isEqualTo("경험 행동형");
        assertThat(briefingJobCount(interviewId)).isEqualTo(2);
        await("페르소나를 바꾼 뒤의 브리핑", () -> {
            for (JsonNode job : apiGet(admin, "/api/v1/interviews/" + interviewId + "/jobs").json.get("items")) {
                if (!"SUCCEEDED".equals(job.get("status").asText())) {
                    return false;
                }
            }
            return true;
        });
        AgentDtos.PersonaPart sent = lastBriefingRequest(jobTitle).getPersona();
        assertThat(sent.getName()).isEqualTo("경험 행동형");
        assertThat(sent.getQuestionType()).isEqualTo("행동 사례 질문");
        assertThat(sent.getFollowUpType()).isEqualTo("빠진 단계 보완");
        assertThat(sent.getGuard()).isEqualTo("가정이 아니라 실제로 있었던 일만 묻습니다.");
        assertThat(sent.getDescription()).isNotEmpty();

        // 고른 페르소나가 꺼지면 기본 선택을 따른다.
        api(HttpMethod.PATCH, admin, "/api/v1/admin/personas/" + starId, "{\"enabled\":false}").expect(200);
        assertThat(apiGet(admin, "/api/v1/interviews/" + interviewId).expect(200).json.get("persona").get("id").asLong())
                .isEqualTo(depthId);

        // 방이 LIVE 면 꺼졌어도 고른 것을 그대로 보여 주고, 바꿀 수 없다. Agent 가 읽는 방 context 에도 실린다.
        long roomId = api(HttpMethod.POST, interviewer, "/api/v1/interviews/" + interviewId + "/room", null).expect(200).json
                .get("id").asLong();
        api(HttpMethod.POST, interviewer, "/api/v1/rooms/" + roomId + "/start", null).expect(200);
        assertThat(apiGet(admin, "/api/v1/interviews/" + interviewId).expect(200).json.get("persona").get("id").asLong())
                .isEqualTo(starId);
        assertThat(api(HttpMethod.PUT, interviewer, url, "{\"personaId\":" + depthId + "}").expect(409).json.get("code").asText())
                .isEqualTo("PERSONA_LOCKED");
        JsonNode context = internal(HttpMethod.GET, "/internal/v1/rooms/" + roomId + "/context", null).expect(200).json;
        assertThat(context.get("persona").get("name").asText()).isEqualTo("경험 행동형");
        assertThat(context.get("persona").get("guard").asText()).isNotEmpty();
        assertThat(context.get("persona").has("id")).isFalse();
        assertThat(context.toString()).doesNotContain(candidate.getName()).doesNotContain(candidate.getApplicantNo());
    }

    @Test
    void briefingIsMadeOnceMoreWhenPersonaChangesWhileItIsRunning() throws Exception {
        final String jobTitle = "페르소나경합직무";
        long recruitmentId = newRecruitment(admin, Track.EXPERIENCED, jobTitle);
        Candidate candidate = newCandidate(recruitmentId, Track.EXPERIENCED, "PERSONA-3", jobTitle, null);
        final long interviewId = newInterview(admin, candidate.getId(), "JOB");
        JsonNode list = apiGet(admin, "/api/v1/personas").expect(200).json.get("items");
        long starId = list.get(2).get("id").asLong(); // 경험 행동형

        // 브리핑 작업을 Agent 호출에서 붙잡아 "도는 중"으로 만든다(요청은 기본 선택인 기술 심층형으로 이미 만들어졌다).
        CountDownLatch release = new CountDownLatch(1);
        fakeAgent.clearRecorded();
        fakeAgent.holdBriefingsOf(jobTitle, release);
        try {
            final long jobId = api(HttpMethod.POST, admin, "/api/v1/interviews/" + interviewId + "/briefing/generate", null)
                    .expect(202).json.get("jobId").asLong();
            await("브리핑 작업이 Agent 를 부름", () -> "RUNNING".equals(apiGet(admin, "/api/v1/ai-jobs/" + jobId).json.path("status").asText())
                    && briefingRequests(jobTitle).size() == 1);

            // 도는 중에 페르소나를 바꾼다: 오류가 아니고, 새 작업을 접수하지도 않는다.
            assertThat(api(HttpMethod.PUT, admin, "/api/v1/interviews/" + interviewId + "/persona", "{\"personaId\":" + starId + "}")
                    .expect(200).json.get("persona").get("name").asText()).isEqualTo("경험 행동형");
            assertThat(briefingJobCount(interviewId)).isEqualTo(1);
        } finally {
            release.countDown();
            fakeAgent.holdBriefingsOf(null, null);
        }

        // 그 작업이 끝나면, 시작할 때의 페르소나와 지금의 페르소나가 다르므로 한 번 더 만든다.
        await("바뀐 페르소나로 다시 만든 브리핑", () -> briefingJobCount(interviewId) == 2 && allJobsSucceeded(interviewId));
        List<AgentDtos.BriefingRequest> sent = briefingRequests(jobTitle);
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).getPersona().getName()).isEqualTo("기술 심층형");
        assertThat(sent.get(1).getPersona().getName()).isEqualTo("경험 행동형");

        // 두 번째 작업은 지금의 페르소나로 만들었으므로 더 반복하지 않는다.
        Thread.sleep(500L);
        assertThat(briefingJobCount(interviewId)).isEqualTo(2);
        assertThat(briefingRequests(jobTitle)).hasSize(2);
    }

    @Test
    void interviewHasNoPersonaWhenNoneIsEnabled() throws Exception {
        long recruitmentId = newRecruitment(admin, Track.EXPERIENCED, "페르소나없음직무");
        Candidate candidate = newCandidate(recruitmentId, Track.EXPERIENCED, "PERSONA-2", "페르소나없음직무", null);
        long interviewId = newInterview(admin, candidate.getId(), "JOB");

        // 추천이 하나도 없으면 쓰는 것 가운데 가장 앞선 것
        List<Persona> all = personaRepository.findAllByOrderByIdAsc();
        for (Persona p : all) {
            p.setRecommended(false);
            personaRepository.save(p);
        }
        assertThat(apiGet(admin, "/api/v1/interviews/" + interviewId).expect(200).json.get("persona").get("name").asText())
                .isEqualTo("기술 심층형");

        // 쓰는 것이 하나도 없으면 null (API 로는 마지막 하나를 끌 수 없어 repository 로 만든 상태)
        for (Persona p : new ArrayList<Persona>(all)) {
            p.setEnabled(false);
            personaRepository.save(p);
        }
        assertThat(apiGet(admin, "/api/v1/interviews/" + interviewId).expect(200).json.get("persona").isNull()).isTrue();
    }

    private int briefingJobCount(long interviewId) throws Exception {
        int count = 0;
        for (JsonNode job : apiGet(admin, "/api/v1/interviews/" + interviewId + "/jobs").expect(200).json.get("items")) {
            if ("BRIEFING".equals(job.get("type").asText())) {
                count++;
            }
        }
        return count;
    }

    private boolean allJobsSucceeded(long interviewId) throws Exception {
        for (JsonNode job : apiGet(admin, "/api/v1/interviews/" + interviewId + "/jobs").expect(200).json.get("items")) {
            if (!"SUCCEEDED".equals(job.get("status").asText())) {
                return false;
            }
        }
        return true;
    }

    private List<AgentDtos.BriefingRequest> briefingRequests(String jobTitle) {
        List<AgentDtos.BriefingRequest> found = new ArrayList<AgentDtos.BriefingRequest>();
        for (Object request : fakeAgent.getRecorded()) {
            if (request instanceof AgentDtos.BriefingRequest && jobTitle.equals(((AgentDtos.BriefingRequest) request).getJobTitle())) {
                found.add((AgentDtos.BriefingRequest) request);
            }
        }
        return found;
    }

    private AgentDtos.BriefingRequest lastBriefingRequest(String jobTitle) {
        AgentDtos.BriefingRequest found = null;
        for (Object request : fakeAgent.getRecorded()) {
            if (request instanceof AgentDtos.BriefingRequest && jobTitle.equals(((AgentDtos.BriefingRequest) request).getJobTitle())) {
                found = (AgentDtos.BriefingRequest) request;
            }
        }
        assertThat(found).as("Fake Agent 가 받은 브리핑 요청").isNotNull();
        return found;
    }
}
