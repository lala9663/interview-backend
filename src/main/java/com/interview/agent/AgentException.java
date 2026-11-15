package com.interview.agent;

import lombok.Getter;

/** Agent 호출 실패. code 는 API 명세의 AGENT_UNAVAILABLE(연결·시간 초과) 또는 MODEL_FAILED(Agent 가 오류 응답). */
@Getter
public class AgentException extends RuntimeException {

    public static final String AGENT_UNAVAILABLE = "AGENT_UNAVAILABLE";
    public static final String MODEL_FAILED = "MODEL_FAILED";

    private final String code;

    public AgentException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
