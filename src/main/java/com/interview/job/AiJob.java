package com.interview.job;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.common.json.JsonNodeConverter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 오래 걸리는 AI 작업 한 건(브리핑, AI 평가, 보고서). 상태를 DB 에 남겨 프론트가 조회한다. */
@Entity
@Table(name = "IV_AI_JOB", indexes = { @Index(name = "IX_IV_AI_JOB_INTERVIEW", columnList = "INTERVIEW_ID"),
        @Index(name = "IX_IV_AI_JOB_STATUS", columnList = "STATUS") })
@Getter
@Setter
@NoArgsConstructor
public class AiJob {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivAiJobSeq")
    @SequenceGenerator(name = "ivAiJobSeq", sequenceName = "IV_AI_JOB_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "JOB_TYPE", nullable = false, length = 20)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "INTERVIEW_ID", nullable = false)
    private Long interviewId;

    /** 작업 인자(JSON). 예: 보고서 {"basis":"REFINED"}, AI 평가 {"finalizeRoom":true,"reportBasis":"LIVE"} */
    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "PAYLOAD", length = 1000)
    private JsonNode payload;

    /** 이 작업을 맡은 서버(app.instance-id). 재시작 복구 때 자기 작업만 고르는 데 쓴다. */
    @Column(name = "WORKER_ID", length = 100)
    private String workerId;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    @Column(name = "FINISHED_AT")
    private LocalDateTime finishedAt;

    @Column(name = "ERROR_CODE", length = 50)
    private String errorCode;

    @Column(name = "ERROR_MESSAGE", length = 1000)
    private String errorMessage;

    public String payloadText(String field) {
        return payload == null || !payload.hasNonNull(field) ? null : payload.get(field).asText();
    }

    public boolean payloadFlag(String field) {
        return payload != null && payload.path(field).asBoolean(false);
    }
}
