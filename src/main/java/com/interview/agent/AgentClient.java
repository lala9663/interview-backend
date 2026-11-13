package com.interview.agent;

import com.interview.agent.dto.AgentDtos;

/**
 * Agent(별도 Python 서비스) 호출의 교체 지점. 계약 4장.
 * app.agent.mode = http ({@link HttpAgentClient}, 기본) | fake ({@link FakeAgentClient}, 테스트·단독 실행)
 *
 * 모든 호출은 동기이고 실패하면 {@link AgentException} 을 던진다.
 * 제한 시간은 요청의 track 에 따라 다르다(app.agent.new-grad.*, app.agent.experienced.*).
 */
public interface AgentClient {

    /** Agent 가 살아 있는지. 예외를 던지지 않는다. */
    boolean isUp();

    AgentDtos.BriefingResponse createBriefing(AgentDtos.BriefingRequest request);

    AgentDtos.EvaluationResponse evaluate(AgentDtos.EvaluationRequest request);

    AgentDtos.ReportResponse createReport(AgentDtos.ReportRequest request);

    /** 방 마무리(남은 음성 처리, 사후 STT 예약). */
    AgentDtos.FinalizeResponse finalizeRoom(Long roomId, boolean postStt);
}
