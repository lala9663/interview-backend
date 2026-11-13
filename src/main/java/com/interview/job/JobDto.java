package com.interview.job;

import java.time.OffsetDateTime;

import com.interview.common.Times;

import lombok.Getter;

/** 계약 2.8 의 Job. */
@Getter
public class JobDto {

    private final Long id;
    private final JobType type;
    private final JobStatus status;
    private final Long interviewId;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime startedAt;
    private final OffsetDateTime finishedAt;
    private final String errorCode;
    private final String errorMessage;

    private JobDto(AiJob job) {
        this.id = job.getId();
        this.type = job.getType();
        this.status = job.getStatus();
        this.interviewId = job.getInterviewId();
        this.createdAt = Times.toOffset(job.getCreatedAt());
        this.startedAt = Times.toOffset(job.getStartedAt());
        this.finishedAt = Times.toOffset(job.getFinishedAt());
        this.errorCode = job.getErrorCode();
        this.errorMessage = job.getErrorMessage();
    }

    public static JobDto from(AiJob job) {
        return new JobDto(job);
    }
}
