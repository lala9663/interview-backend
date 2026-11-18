package com.interview.briefing;

import org.springframework.stereotype.Component;

import com.interview.agent.AgentClient;
import com.interview.agent.dto.AgentDtos;
import com.interview.job.AiJob;
import com.interview.job.AiJobHandler;
import com.interview.job.JobStatus;
import com.interview.job.JobType;

import lombok.RequiredArgsConstructor;

/** BRIEFING 작업: Agent /briefings 를 불러 요약과 질문을 저장한다. 만드는 동안 페르소나가 바뀌었으면 끝난 뒤 한 번 더 접수한다. */
@Component
@RequiredArgsConstructor
public class BriefingJobHandler implements AiJobHandler {

    private final BriefingService briefingService;
    private final AgentClient agentClient;

    @Override
    public JobType type() {
        return JobType.BRIEFING;
    }

    @Override
    public void handle(AiJob job) {
        briefingService.markStatus(job.getInterviewId(), JobStatus.RUNNING);
        // 요청 만들기와 결과 저장은 각각 짧은 트랜잭션이고, Agent 호출은 그 사이(트랜잭션 밖)에 있다.
        BriefingService.Prepared prepared = briefingService.prepare(job.getInterviewId());
        AgentDtos.BriefingResponse response = agentClient.createBriefing(prepared.getRequest());
        briefingService.saveResult(job.getInterviewId(), response, prepared.getPersonaId());
    }

    @Override
    public void onFinished(AiJob job, boolean success) {
        if (!success) {
            briefingService.markStatus(job.getInterviewId(), JobStatus.FAILED);
            return;
        }
        // 만드는 동안 면접의 페르소나가 바뀌었으면 새 페르소나로 한 번 더 만든다(새 작업으로 접수).
        briefingService.regenerateIfPersonaChanged(job.getInterviewId());
    }
}
