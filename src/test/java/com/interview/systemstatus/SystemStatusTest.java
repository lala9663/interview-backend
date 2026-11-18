package com.interview.systemstatus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.agent.FakeAgentClient;
import com.interview.agent.dto.AgentDtos;
import com.interview.common.Times;
import com.interview.job.AiJob;
import com.interview.job.AiJobRepository;
import com.interview.job.JobStatus;
import com.interview.job.JobType;
import com.interview.room.Room;
import com.interview.room.RoomRepository;
import com.interview.room.RoomStatus;
import com.interview.support.ApiTestSupport;

/** 시스템 상태(API 명세 2.13)의 모양과 서비스 상태 규칙. */
class SystemStatusTest extends ApiTestSupport {

    @Autowired
    private FakeAgentClient fakeAgent;

    @Autowired
    private AiJobRepository jobRepository;

    @Autowired
    private RoomRepository roomRepository;

    private final List<AiJob> created = new ArrayList<AiJob>();

    @Autowired
    private SystemStatusService systemStatusService;

    @AfterEach
    void restore() {
        fakeAgent.setFakeHealth(null);
        systemStatusService.invalidate();
        jobRepository.deleteAll(created);
        created.clear();
    }

    @Test
    void statusHasServicesWaitsQueueAndDailySessions() throws Exception {
        apiGet(null, "/api/v1/system/status").expect(401);
        String interviewer = login("E1002"); // 로그인한 사용자 모두

        // 대기열과 기다리는 시간에 잡힐 작업, 오늘 시작한 방을 하나씩 넣는다(다른 테스트의 데이터와 섞이므로 "이상"으로 확인한다).
        long interviewId = 987654321L; // 집계는 면접을 따지지 않는다
        job(interviewId, JobType.AI_EVALUATION, JobStatus.PENDING, 0);
        job(interviewId, JobType.AI_EVALUATION, JobStatus.RUNNING, 0);
        job(interviewId, JobType.AI_EVALUATION, JobStatus.SUCCEEDED, 40);
        Room room = new Room();
        room.setInterviewId(interviewId);
        room.setStatus(RoomStatus.LIVE);
        room.setCreatedAt(Times.now());
        room.setStartedAt(Times.now());
        roomRepository.save(room);

        JsonNode body;
        try {
            systemStatusService.invalidate(); // 다른 테스트가 방금 만든 응답을 기억하고 있을 수 있다
            body = apiGet(interviewer, "/api/v1/system/status").expect(200).json;
        } finally {
            roomRepository.delete(room);
        }
        assertThat(body.get("checkedAt").asText()).isNotEmpty();

        // services: 정해진 다섯 가지, 이 순서. Fake Agent 는 OK 이고 llmStatus·sttStatus 가 없으므로 STT·LLM 은 AGENT 를 따른다.
        String[] keys = { "BACKEND", "DB", "AGENT", "STT", "LLM" };
        assertThat(body.get("services")).hasSize(keys.length);
        for (int n = 0; n < keys.length; n++) {
            JsonNode s = body.get("services").get(n);
            assertThat(s.get("key").asText()).isEqualTo(keys[n]);
            assertThat(s.get("name").asText()).isNotEmpty();
            assertThat(s.get("detail").asText()).isNotEmpty();
            assertThat(s.get("status").asText()).isEqualTo("OK");
            assertThat(s.get("note").asText()).isEmpty();
        }

        // queue: 작업 종류마다 한 줄
        Map<String, JsonNode> queue = byKey(body.get("queue"));
        assertThat(queue.keySet()).containsExactlyInAnyOrder("BRIEFING", "AI_EVALUATION", "REPORT");
        assertThat(queue.get("BRIEFING").get("label").asText()).isEqualTo("사전 리포트와 질문");
        assertThat(queue.get("REPORT").get("label").asText()).isEqualTo("면접 보고서");
        assertThat(queue.get("AI_EVALUATION").get("label").asText()).isEqualTo("AI 참고 평가");
        assertThat(queue.get("AI_EVALUATION").get("waiting").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(queue.get("AI_EVALUATION").get("running").asInt()).isGreaterThanOrEqualTo(1);

        // waits: 끝난 작업이 있는 종류만. 방금 넣은 40초짜리가 가장 최근이라 최근 5건 평균이 0 보다 크다.
        Map<String, JsonNode> waits = byKey(body.get("waits"));
        JsonNode wait = waits.get("AI_EVALUATION");
        assertThat(wait.get("label").asText()).isEqualTo("AI 참고 평가");
        assertThat(wait.get("seconds").asInt()).isGreaterThan(0);
        assertThat(wait.get("usual").asInt()).isGreaterThanOrEqualTo(0);

        // dailySessions: 오늘을 포함한 7일, 날짜 오름차순
        JsonNode days = body.get("dailySessions");
        assertThat(days).hasSize(7);
        assertThat(days.get(6).get("date").asText()).isEqualTo(Times.today().toString());
        assertThat(days.get(0).get("date").asText()).isEqualTo(Times.today().minusDays(6).toString());
        assertThat(days.get(6).get("count").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void sttAndLlmFollowAgentHealth() throws Exception {
        String admin = login("E1001");

        // Agent 가 llmStatus·sttStatus 를 주면 그 값을 쓴다.
        fakeAgent.setFakeHealth(new AgentDtos.Health("UP", "DEGRADED", "OK"));
        systemStatusService.invalidate();
        Map<String, JsonNode> services = byKey(apiGet(admin, "/api/v1/system/status").expect(200).json.get("services"));
        assertThat(services.get("AGENT").get("status").asText()).isEqualTo("OK");
        assertThat(services.get("STT").get("status").asText()).isEqualTo("OK");
        assertThat(services.get("LLM").get("status").asText()).isEqualTo("DEGRADED");
        assertThat(services.get("LLM").get("note").asText()).isNotEmpty();

        // Agent 에 닿지 못하면 AGENT·STT·LLM 이 DOWN 이고, 백엔드와 DB 는 그대로다.
        fakeAgent.setFakeHealth(new AgentDtos.Health("DOWN", null, null));
        // 응답은 5초 동안 기억한다: 바로 다시 물으면 방금 만든 것이 그대로 온다.
        JsonNode remembered = apiGet(admin, "/api/v1/system/status").expect(200).json;
        assertThat(byKey(remembered.get("services")).get("LLM").get("status").asText()).isEqualTo("DEGRADED");
        systemStatusService.invalidate();
        services = byKey(apiGet(admin, "/api/v1/system/status").expect(200).json.get("services"));
        assertThat(services.get("BACKEND").get("status").asText()).isEqualTo("OK");
        assertThat(services.get("DB").get("status").asText()).isEqualTo("OK");
        assertThat(services.get("AGENT").get("status").asText()).isEqualTo("DOWN");
        assertThat(services.get("AGENT").get("note").asText()).isNotEmpty();
        assertThat(services.get("STT").get("status").asText()).isEqualTo("DOWN");
        assertThat(services.get("LLM").get("status").asText()).isEqualTo("DOWN");
    }

    private void job(long interviewId, JobType type, JobStatus status, int tookSeconds) {
        AiJob job = new AiJob();
        job.setType(type);
        job.setStatus(status);
        job.setInterviewId(interviewId);
        job.setWorkerId("system-status-test");
        job.setCreatedAt(Times.now().minusSeconds(tookSeconds));
        if (status == JobStatus.SUCCEEDED) {
            job.setStartedAt(job.getCreatedAt());
            // 가장 최근에 끝난 작업으로 잡히게 조금 뒤의 시각을 넣는다.
            job.setFinishedAt(Times.now().plusSeconds(5));
        }
        created.add(jobRepository.save(job));
    }

    private static Map<String, JsonNode> byKey(JsonNode items) {
        Map<String, JsonNode> map = new HashMap<String, JsonNode>();
        for (JsonNode item : items) {
            map.put(item.get("key").asText(), item);
        }
        return map;
    }
}
