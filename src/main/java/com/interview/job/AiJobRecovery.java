package com.interview.job;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 서버가 다시 뜰 때, 이 서버(workerId)가 맡았다가 끝내지 못한 작업(PENDING/RUNNING)을 정리한다.
 * app.jobs.recovery = fail(기본) | restart
 */
@Component
@Order(100)
@RequiredArgsConstructor
public class AiJobRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiJobRecovery.class);

    private final AiJobRepository jobRepository;
    private final AiJobService jobService;
    private final AiJobRunner runner;
    private final JobProperties props;
    private final JobConfig.WorkerId workerId;

    @Override
    public void run(ApplicationArguments args) {
        List<AiJob> stale = jobRepository.findByStatusInAndWorkerId(
                Arrays.asList(JobStatus.PENDING, JobStatus.RUNNING), workerId.get());
        if (stale.isEmpty()) {
            return;
        }
        boolean restart = "restart".equalsIgnoreCase(props.getRecovery());
        log.warn("끝나지 않은 AI 작업 {}건을 {} 처리합니다.", stale.size(), restart ? "재시작" : "FAILED");
        for (AiJob job : stale) {
            if (restart) {
                jobService.requeue(job.getId());
                continue;
            }
            jobService.markFailed(job.getId(), "SERVER_RESTARTED", "서버가 다시 시작되어 작업이 중단되었습니다. 다시 요청해 주세요.");
            AiJobHandler handler = runner.handlerOf(job.getType());
            if (handler != null) {
                try {
                    handler.onFinished(job, false);
                } catch (Exception e) {
                    log.error("중단된 작업 후처리 실패 jobId={}", job.getId(), e);
                }
            }
        }
    }
}
