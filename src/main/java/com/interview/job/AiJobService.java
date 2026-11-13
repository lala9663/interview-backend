package com.interview.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interview.common.ApiException;
import com.interview.common.Times;
import com.interview.common.json.Json;

import lombok.RequiredArgsConstructor;

/**
 * AI 작업을 접수하고 상태를 바꾼다.
 * submit 은 PENDING 행을 만들고 이벤트를 낸다. 트랜잭션이 커밋된 뒤 AiJobRunner 가 스레드풀에서 실행한다.
 */
@Service
@RequiredArgsConstructor
public class AiJobService {

    private final AiJobRepository jobRepository;
    private final ApplicationEventPublisher events;
    private final JobConfig.WorkerId workerId;

    @Transactional
    public AiJob submit(JobType type, Long interviewId, Map<String, Object> payload) {
        AiJob job = new AiJob();
        job.setType(type);
        job.setStatus(JobStatus.PENDING);
        job.setInterviewId(interviewId);
        job.setPayload(payload == null || payload.isEmpty() ? null : Json.toTree(payload));
        job.setWorkerId(workerId.get());
        job.setCreatedAt(Times.now());
        jobRepository.save(job);
        events.publishEvent(new AiJobSubmitted(job.getId()));
        return job;
    }

    @Transactional(readOnly = true)
    public JobDto get(Long jobId) {
        return JobDto.from(jobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("JOB_NOT_FOUND", "작업을 찾을 수 없습니다.")));
    }

    @Transactional(readOnly = true)
    public List<JobDto> listByInterview(Long interviewId) {
        List<JobDto> items = new ArrayList<JobDto>();
        for (AiJob job : jobRepository.findByInterviewIdOrderByIdDesc(interviewId)) {
            items.add(JobDto.from(job));
        }
        return items;
    }

    /** 가장 최근 작업의 상태. 없으면 null. */
    @Transactional(readOnly = true)
    public JobStatus latestStatus(Long interviewId, JobType type) {
        AiJob job = jobRepository.findFirstByInterviewIdAndTypeOrderByIdDesc(interviewId, type).orElse(null);
        return job == null ? null : job.getStatus();
    }

    /** PENDING → RUNNING. 이미 다른 상태면 null (중복 실행 방지). */
    @Transactional
    public AiJob markRunning(Long jobId) {
        AiJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.PENDING) {
            return null;
        }
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(Times.now());
        job.setWorkerId(workerId.get());
        return job;
    }

    @Transactional
    public void markSucceeded(Long jobId) {
        AiJob job = jobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus(JobStatus.SUCCEEDED);
            job.setFinishedAt(Times.now());
            job.setErrorCode(null);
            job.setErrorMessage(null);
        }
    }

    @Transactional
    public void markFailed(Long jobId, String errorCode, String errorMessage) {
        AiJob job = jobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus(JobStatus.FAILED);
            job.setFinishedAt(Times.now());
            job.setErrorCode(errorCode);
            job.setErrorMessage(errorMessage != null && errorMessage.length() > 1000
                    ? errorMessage.substring(0, 1000) : errorMessage);
        }
    }

    /** 재실행용: 상태를 PENDING 으로 되돌리고 다시 실행 이벤트를 낸다. */
    @Transactional
    public void requeue(Long jobId) {
        AiJob job = jobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus(JobStatus.PENDING);
            job.setStartedAt(null);
            events.publishEvent(new AiJobSubmitted(job.getId()));
        }
    }

    /** 작업이 접수됐다는 이벤트. */
    public static class AiJobSubmitted {
        private final Long jobId;

        public AiJobSubmitted(Long jobId) {
            this.jobId = jobId;
        }

        public Long getJobId() {
            return jobId;
        }
    }
}
