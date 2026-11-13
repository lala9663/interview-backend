package com.interview.briefing;

import org.springframework.stereotype.Component;

import com.interview.agent.AgentClient;
import com.interview.agent.dto.AgentDtos;
import com.interview.job.AiJob;
import com.interview.job.AiJobHandler;
import com.interview.job.JobStatus;
import com.interview.job.JobType;

import lombok.RequiredArgsConstructor;

/** BRIEFING 작업: Agent /briefings 를 불러 요약과 질문을 저장한다. */
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
        AgentDtos.BriefingRequest request = briefingService.buildRequest(job.getInterviewId());
        AgentDtos.BriefingResponse response = agentClient.createBriefing(request);
        briefingService.saveResult(job.getInterviewId(), response);
    }

    @Override
    public void onFinished(AiJob job, boolean success) {
        if (!success) {
            briefingService.markStatus(job.getInterviewId(), JobStatus.FAILED);
        }
    }
}
