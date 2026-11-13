package com.interview.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.dto.AgentDtos;
import com.interview.auth.AppSecurityProperties;
import com.interview.candidate.CandidateProfile;
import com.interview.interview.InterviewType;
import com.interview.recruitment.Track;
import com.interview.report.ReportBasis;
import com.interview.report.ReportDetail;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/** HttpAgentClient 가 계약 4장대로 부르는지(경로, X-Internal-Key, 본문 모양, 오류 변환)를 가짜 HTTP 서버로 확인한다. */
class HttpAgentClientTest {

    private HttpServer server;
    private HttpAgentClient client;
    private final Map<String, String> receivedBody = new ConcurrentHashMap<String, String>();
    private final Map<String, String> receivedKey = new ConcurrentHashMap<String, String>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        respond("/health", 200, "{\"status\":\"UP\",\"llm\":\"fake\",\"stt\":\"fake\"}");
        respond("/internal/v1/briefings", 200, "{\"summary\":{\"headline\":\"요약\",\"keyPoints\":[]},"
                + "\"questions\":[{\"source\":\"RESUME\",\"category\":\"프로젝트\",\"text\":\"질문?\",\"intent\":\"의도\"}],"
                + "\"excluded\":[\"family\"],\"model\":\"m1\",\"unknownField\":1}");
        respond("/internal/v1/reports", 500, "{\"code\":\"MODEL_FAILED\",\"message\":\"모델이 응답하지 않습니다.\",\"details\":{}}");
        respond("/internal/v1/rooms/9001/finalize", 202, "{\"flushed\":true,\"postSttScheduled\":true,\"audioRetention\":\"MEMORY_ONLY\"}");
        server.start();
        client = newClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void callsAgentAsTheContractSays() throws Exception {
        assertThat(client.isUp()).isTrue();

        AgentDtos.BriefingRequest request = new AgentDtos.BriefingRequest();
        request.setTrack(Track.NEW_GRAD);
        request.setInterviewType(InterviewType.PT);
        request.setJobTitle("SW개발");
        request.setJdText("JD");
        request.setCandidate(new AgentDtos.CandidatePart(new CandidateProfile()));
        request.getQuestionBank().add(new AgentDtos.BankItem(31L, "CS기초", "질문"));
        request.setQuestionCount(new AgentDtos.QuestionCount(4, 6));
        AgentDtos.BriefingResponse response = client.createBriefing(request);

        assertThat(response.getModel()).isEqualTo("m1");
        assertThat(response.getSummary().get("headline").asText()).isEqualTo("요약");
        assertThat(response.getQuestions()).hasSize(1);
        assertThat(response.getExcluded()).containsExactly("family");
        assertThat(receivedKey.get("/internal/v1/briefings")).isEqualTo("dev-internal-key");
        com.fasterxml.jackson.databind.JsonNode sent = new ObjectMapper().readTree(receivedBody.get("/internal/v1/briefings"));
        assertThat(sent.get("track").asText()).isEqualTo("NEW_GRAD");
        assertThat(sent.get("interviewType").asText()).isEqualTo("PT");
        assertThat(sent.get("candidate").get("profile").has("essays")).isTrue();
        assertThat(sent.get("questionBank").get(0).get("id").asLong()).isEqualTo(31L);
        assertThat(sent.get("questionCount").get("resume").asInt()).isEqualTo(6);

        AgentDtos.FinalizeResponse finalized = client.finalizeRoom(9001L, true);
        assertThat(finalized.isPostSttScheduled()).isTrue();
        assertThat(receivedBody.get("/internal/v1/rooms/9001/finalize")).contains("\"postStt\":true");
    }

    @Test
    void agentErrorResponseBecomesAgentExceptionWithItsCode() {
        AgentDtos.ReportRequest request = new AgentDtos.ReportRequest();
        request.setTrack(Track.EXPERIENCED);
        request.setDetail(ReportDetail.DETAILED);
        request.setBasis(ReportBasis.REFINED);
        Throwable thrown = catchThrowable(() -> client.createReport(request));
        assertThat(thrown).isInstanceOf(AgentException.class);
        assertThat(((AgentException) thrown).getCode()).isEqualTo("MODEL_FAILED");
        assertThat(thrown.getMessage()).isEqualTo("모델이 응답하지 않습니다.");
    }

    @Test
    void deadAgentIsUnavailableNotACrash() {
        int port = server.getAddress().getPort();
        server.stop(0);
        HttpAgentClient dead = newClient("http://127.0.0.1:" + port);
        assertThat(dead.isUp()).isFalse();
        AgentDtos.EvaluationRequest request = new AgentDtos.EvaluationRequest();
        request.setTrack(Track.NEW_GRAD);
        Throwable thrown = catchThrowable(() -> dead.evaluate(request));
        assertThat(thrown).isInstanceOf(AgentException.class);
        assertThat(((AgentException) thrown).getCode()).isEqualTo("AGENT_UNAVAILABLE");
    }

    private static HttpAgentClient newClient(String baseUrl) {
        AgentProperties props = new AgentProperties();
        props.setBaseUrl(baseUrl);
        props.setConnectTimeoutSeconds(2);
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new HttpAgentClient(props, new AppSecurityProperties(), mapper);
    }

    private void respond(final String path, final int status, final String body) {
        server.createContext(path, new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                receivedBody.put(path, read(exchange.getRequestBody()));
                String key = exchange.getRequestHeaders().getFirst("X-Internal-Key");
                receivedKey.put(path, key == null ? "" : key);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                OutputStream out = exchange.getResponseBody();
                out.write(bytes);
                out.close();
            }
        });
    }

    private static String read(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
