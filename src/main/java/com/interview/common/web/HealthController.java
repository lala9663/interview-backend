package com.interview.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interview.agent.AgentClient;

import lombok.RequiredArgsConstructor;

/** GET /api/v1/health (인증 없음). Agent 가 죽어 있어도 백엔드는 UP 이다. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HealthController {

    private final AgentClient agentClient;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("status", "UP");
        body.put("agent", agentClient.isUp() ? "UP" : "DOWN");
        return body;
    }
}
