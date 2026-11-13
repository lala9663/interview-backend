package com.interview.evaluation;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.interview.agent.AgentClient;
import com.interview.agent.AgentException;
import com.interview.agent.dto.AgentDtos;
import com.interview.job.AiJob;
import com.interview.job.AiJobHandler;
import com.interview.job.AiJobService;
import com.interview.job.JobType;
import com.interview.room.RoomService;

import lombok.RequiredArgsConstructor;

/**
 * AI_EVALUATION 작업.
 * payload:
 * - finalizeRoomId: 있으면 먼저 Agent 에 방 마무리(finalize)를 요청한다(방 종료 직후의 작업).
 * - reportBasis   : 있으면 이 작업이 끝난 뒤(성공·실패 모두) 그 기준의 보고서 작업을 이어서 접수한다.
 *
 * 그래서 방 종료 한 번이 finalize → AI 평가 → 보고서(LIVE) 로 이어진다.
 */
@Component
@RequiredArgsConstructor
public class AiEvaluationJobHandler implements AiJobHandler {

    private static final Logger log = LoggerFactory.getLogger(AiEvaluationJobHandler.class);

    private final AiEvaluationService aiEvaluationService;
    private final AgentClient agentClient;
    private final RoomService roomService;
    private final AiJobService jobService;

    @Override
    public JobType type() {
        return JobType.AI_EVALUATION;
    }

    @Override
    public void handle(AiJob job) {
        if (job.getPayload() != null && job.getPayload().hasNonNull("finalizeRoomId")) {
            Long roomId = job.getPayload().get("finalizeRoomId").asLong();
            try {
                agentClient.finalizeRoom(roomId, true);
            } catch (AgentException e) {
                // finalize 가 실패해도(Agent 가 죽어 있는 등) 이미 저장된 전사로 평가를 시도한다.
                log.warn("Agent finalize 실패 roomId={} code={}", roomId, e.getCode());
                roomService.recordEvent(roomId, "FINALIZE_FAILED", e.getMessage(), null);
            }
        }
        AgentDtos.EvaluationRequest request = aiEvaluationService.buildRequest(job.getInterviewId());
        AgentDtos.EvaluationResponse response = agentClient.evaluate(request);
        aiEvaluationService.saveResult(job.getInterviewId(), response);
    }

    @Override
    public void onFinished(AiJob job, boolean success) {
        String reportBasis = job.payloadText("reportBasis");
        if (reportBasis != null) {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("basis", reportBasis);
            jobService.submit(JobType.REPORT, job.getInterviewId(), payload);
        }
    }
}
