package com.interview.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 통합 테스트의 공통 바탕: local + test 프로파일(메모리 H2, Fake Agent, 시드 데이터)로 앱 전체를 띄우고 MockMvc 로 부른다.
 * 테스트 클래스들이 같은 설정을 쓰므로 Spring 컨텍스트(와 DB)는 한 번만 만들어져 공유된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "local", "test" })
public abstract class ApiTestSupport {

    protected static final String INTERNAL_KEY = "dev-internal-key";
    protected static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** HTTP 응답 한 건(상태 + JSON 본문). */
    public static class Result {
        public final int status;
        public final String text;
        public final JsonNode json;

        Result(int status, String text, JsonNode json) {
            this.status = status;
            this.text = text;
            this.json = json;
        }

        public Result expect(int expectedStatus) {
            assertThat(status).as("응답: %s", text).isEqualTo(expectedStatus);
            return this;
        }
    }

    protected String login(String employeeNo) throws Exception {
        Result r = call(HttpMethod.POST, "/api/v1/auth/dev-login", null, "{\"employeeNo\":\"" + employeeNo + "\"}").expect(200);
        return r.json.get("accessToken").asText();
    }

    protected Result apiGet(String token, String url) throws Exception {
        return call(HttpMethod.GET, url, token, null);
    }

    protected Result api(HttpMethod method, String token, String url, String body) throws Exception {
        return call(method, url, token, body);
    }

    /** Agent 인 척 내부 API 를 부른다. */
    protected Result internal(HttpMethod method, String url, String body) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, url).header("X-Internal-Key", INTERNAL_KEY);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body.getBytes(StandardCharsets.UTF_8));
        }
        return perform(builder);
    }

    protected Result upload(String token, Long recruitmentId, String fileName, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, XLSX, content);
        MockHttpServletRequestBuilder builder = multipart("/api/v1/recruitments/" + recruitmentId + "/uploads").file(file);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return perform(builder);
    }

    protected byte[] download(String token, String url) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return response.getContentAsByteArray();
    }

    protected Long newGradRecruitmentId(String token) throws Exception {
        return apiGet(token, "/api/v1/recruitments?track=NEW_GRAD").expect(200).json.get("items").get(0).get("id").asLong();
    }

    /** 조건이 참이 될 때까지 기다린다(비동기 AI 작업용). */
    protected void await(String what, Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 30000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.call()) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("시간 안에 끝나지 않음: " + what);
    }

    private Result call(HttpMethod method, String url, String token, String body) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, url);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body.getBytes(StandardCharsets.UTF_8));
        }
        return perform(builder);
    }

    private Result perform(MockHttpServletRequestBuilder builder) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String text = response.getContentAsString(StandardCharsets.UTF_8);
        JsonNode json = null;
        if (!text.isEmpty() && text.trim().startsWith("{")) {
            json = objectMapper.readTree(text);
        }
        return new Result(response.getStatus(), text, json);
    }
}
