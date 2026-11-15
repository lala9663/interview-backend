package com.interview.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.dto.AgentDtos;
import com.interview.auth.AppSecurityProperties;
import com.interview.internal.InternalKeyFilter;

/** Agent 를 HTTP 로 부르는 구현(API 명세 4장). 제한 시간별로 RestTemplate 을 하나씩 만들어 재사용한다. */
@Component
@ConditionalOnProperty(name = "app.agent.mode", havingValue = "http", matchIfMissing = true)
public class HttpAgentClient implements AgentClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAgentClient.class);
    private static final long HEALTH_CACHE_MILLIS = 5000L;

    private final AgentProperties props;
    private final String internalKey;
    private final ObjectMapper objectMapper;
    private final Map<Integer, RestTemplate> templates = new ConcurrentHashMap<Integer, RestTemplate>();

    private volatile long healthCheckedAt;
    private volatile boolean healthUp;

    public HttpAgentClient(AgentProperties props, AppSecurityProperties securityProps, ObjectMapper objectMapper) {
        this.props = props;
        this.internalKey = securityProps.getInternalKey();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isUp() {
        long now = System.currentTimeMillis();
        if (now - healthCheckedAt < HEALTH_CACHE_MILLIS) {
            return healthUp;
        }
        boolean up;
        try {
            JsonNode body = template(props.getHealthTimeoutSeconds()).getForObject(props.getBaseUrl() + "/health",
                    JsonNode.class);
            up = body != null && "UP".equalsIgnoreCase(body.path("status").asText());
        } catch (RestClientException e) {
            up = false;
        }
        healthUp = up;
        healthCheckedAt = now;
        return up;
    }

    @Override
    public AgentDtos.BriefingResponse createBriefing(AgentDtos.BriefingRequest request) {
        return post("/internal/v1/briefings", request, AgentDtos.BriefingResponse.class,
                props.timeouts(request.getTrack()).getBriefingSeconds());
    }

    @Override
    public AgentDtos.EvaluationResponse evaluate(AgentDtos.EvaluationRequest request) {
        return post("/internal/v1/evaluations", request, AgentDtos.EvaluationResponse.class,
                props.timeouts(request.getTrack()).getEvaluationSeconds());
    }

    @Override
    public AgentDtos.ReportResponse createReport(AgentDtos.ReportRequest request) {
        return post("/internal/v1/reports", request, AgentDtos.ReportResponse.class,
                props.timeouts(request.getTrack()).getReportSeconds());
    }

    @Override
    public AgentDtos.FinalizeResponse finalizeRoom(Long roomId, boolean postStt) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("postStt", postStt);
        return post("/internal/v1/rooms/" + roomId + "/finalize", body, AgentDtos.FinalizeResponse.class,
                props.getFinalizeTimeoutSeconds());
    }

    @Override
    public AgentDtos.StatsResponse stats() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(InternalKeyFilter.HEADER, internalKey);
        try {
            AgentDtos.StatsResponse result = template(props.getStatsTimeoutSeconds())
                    .exchange(props.getBaseUrl() + "/internal/v1/stats", HttpMethod.GET, new HttpEntity<Object>(headers),
                            AgentDtos.StatsResponse.class)
                    .getBody();
            if (result == null) {
                throw new AgentException(AgentException.AGENT_UNAVAILABLE, "Agent 통계 응답이 비어 있습니다.", null);
            }
            return result;
        } catch (RestClientException e) {
            throw new AgentException(AgentException.AGENT_UNAVAILABLE, "Agent 통계를 읽을 수 없습니다.", e);
        }
    }

    private <T> T post(String path, Object body, Class<T> responseType, int readTimeoutSeconds) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(InternalKeyFilter.HEADER, internalKey);
        try {
            T result = template(readTimeoutSeconds)
                    .exchange(props.getBaseUrl() + path, HttpMethod.POST, new HttpEntity<Object>(body, headers), responseType)
                    .getBody();
            if (result == null) {
                throw new AgentException(AgentException.MODEL_FAILED, "Agent 응답이 비어 있습니다: " + path, null);
            }
            return result;
        } catch (HttpStatusCodeException e) {
            // Agent 가 오류 응답을 줌. 본문이 API 명세의 오류 모양이면 code/message 를 그대로 쓴다.
            String code = AgentException.MODEL_FAILED;
            String message = "Agent 오류 응답(" + e.getRawStatusCode() + "): " + path;
            try {
                JsonNode error = objectMapper.readTree(e.getResponseBodyAsString());
                if (error.hasNonNull("code")) {
                    code = error.get("code").asText();
                }
                if (error.hasNonNull("message")) {
                    message = error.get("message").asText();
                }
            } catch (Exception ignore) {
                // 본문이 JSON 이 아니면 기본 메시지를 쓴다.
            }
            throw new AgentException(code, message, e);
        } catch (RestClientException e) {
            // 연결 실패, 시간 초과, 응답 해석 실패
            log.debug("Agent 호출 실패 {}", path, e);
            throw new AgentException(AgentException.AGENT_UNAVAILABLE, "Agent 를 호출할 수 없습니다: " + path, e);
        }
    }

    private RestTemplate template(final int readTimeoutSeconds) {
        RestTemplate template = templates.get(readTimeoutSeconds);
        if (template == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(props.getConnectTimeoutSeconds() * 1000);
            factory.setReadTimeout(readTimeoutSeconds * 1000);
            template = new RestTemplate(factory);
            template.setMessageConverters(Collections.<HttpMessageConverter<?>>singletonList(
                    new MappingJackson2HttpMessageConverter(objectMapper)));
            templates.put(readTimeoutSeconds, template);
        }
        return template;
    }
}
