package com.interview.evaluation;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.common.json.JsonNodeConverter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 면접관 한 명의 평가(메모 포함). 면접 × 면접관마다 하나. */
@Entity
@Table(name = "IV_EVALUATION", uniqueConstraints = @UniqueConstraint(name = "UK_IV_EVALUATION", columnNames = {
        "INTERVIEW_ID", "USER_ID" }))
@Getter
@Setter
@NoArgsConstructor
public class InterviewerEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivEvaluationSeq")
    @SequenceGenerator(name = "ivEvaluationSeq", sequenceName = "IV_EVALUATION_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "INTERVIEW_ID", nullable = false)
    private Long interviewId;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Lob
    @Column(name = "MEMO")
    private String memo;

    /** [ { "criterionCode", "score", "comment" } ] */
    @Lob
    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "SCORES_JSON")
    private JsonNode scores;

    @Column(name = "GRADE", length = 20)
    private String grade;

    @Column(name = "IS_SUBMITTED", nullable = false)
    private boolean submitted;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "SUBMITTED_AT")
    private LocalDateTime submittedAt;
}
