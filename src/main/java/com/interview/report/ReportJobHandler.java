package com.interview.report;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.interview.agent.AgentClient;
import com.interview.agent.AgentException;
import com.interview.agent.dto.AgentDtos;
import com.interview.job.AiJob;
import com.interview.job.AiJobHandler;
import com.interview.job.JobType;

import lombok.RequiredArgsConstructor;

/**
 * REPORT 작업. payload.basis = LIVE | REFINED (없으면 LIVE).
 * 상세도는 트랙이 정한다: 신입 BRIEF, 경력 DETAILED. 제한 시간도 트랙별이다(app.agent.*).
 */
@Component
@RequiredArgsConstructor
public class ReportJobHandler implements AiJobHandler {

    private static final int SAVE_ATTEMPTS = 3;

    private final ReportService reportService;
    private final AgentClient agentClient;

    @Override
    public JobType type() {
        return JobType.REPORT;
    }

    @Override
    public void handle(AiJob job) {
        String basisText = job.payloadText("basis");
        ReportBasis basis = basisText == null ? ReportBasis.LIVE : ReportBasis.valueOf(basisText);
        AgentDtos.ReportRequest request = reportService.buildRequest(job.getInterviewId(), basis);
        AgentDtos.ReportResponse response = agentClient.createReport(request);
        if (response.getContent() == null || !response.getContent().isObject()) {
            throw new AgentException(AgentException.MODEL_FAILED, "보고서 내용이 비어 있습니다.", null);
        }
        saveWithRetry(job.getInterviewId(), basis, response);
    }

    /** 초안과 보정본 작업이 같은 버전 번호를 잡으면 한쪽이 유니크 제약에 걸린다. 번호를 다시 읽어 저장한다. */
    private void saveWithRetry(Long interviewId, ReportBasis basis, AgentDtos.ReportResponse response) {
        DataIntegrityViolationException last = null;
        for (int attempt = 0; attempt < SAVE_ATTEMPTS; attempt++) {
            try {
                reportService.saveNewVersion(interviewId, basis, response);
                return;
            } catch (DataIntegrityViolationException e) {
                last = e;
            }
        }
        throw last;
    }
}
