package com.interview.job;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.interview.agent.AgentException;

/**
 * 접수된 AI 작업을 스레드풀에서 실행한다.
 * PENDING → RUNNING → (handler.handle) → SUCCEEDED | FAILED → handler.onFinished
 */
@Component
public class AiJobRunner {

    private static final Logger log = LoggerFactory.getLogger(AiJobRunner.class);

    private final AiJobService jobService;
    private final Map<JobType, AiJobHandler> handlers = new EnumMap<JobType, AiJobHandler>(JobType.class);

    public AiJobRunner(AiJobService jobService, List<AiJobHandler> handlerList) {
        this.jobService = jobService;
        for (AiJobHandler h : handlerList) {
            handlers.put(h.type(), h);
        }
    }

    /** 작업을 접수한 트랜잭션이 커밋된 뒤에 실행된다(트랜잭션 밖에서 접수됐으면 바로). */
    @Async(JobConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSubmitted(AiJobService.AiJobSubmitted event) {
        run(event.getJobId());
    }

    public void run(Long jobId) {
        AiJob job = jobService.markRunning(jobId);
        if (job == null) {
            return;
        }
        AiJobHandler handler = handlers.get(job.getType());
        boolean success = false;
        try {
            if (handler == null) {
                throw new IllegalStateException("실행기가 없는 작업 유형입니다: " + job.getType());
            }
            handler.handle(job);
            jobService.markSucceeded(jobId);
            success = true;
        } catch (AgentException e) {
            log.warn("AI 작업 실패 jobId={} type={} code={} message={}", jobId, job.getType(), e.getCode(), e.getMessage());
            jobService.markFailed(jobId, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("AI 작업 실패 jobId={} type={}", jobId, job.getType(), e);
            jobService.markFailed(jobId, "JOB_FAILED", e.getMessage());
        }
        if (handler != null) {
            try {
                handler.onFinished(job, success);
            } catch (Exception e) {
                log.error("AI 작업 후처리 실패 jobId={}", jobId, e);
            }
        }
    }

    public AiJobHandler handlerOf(JobType type) {
        return handlers.get(type);
    }
}
