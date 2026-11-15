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

/** AI 참고 평가. 면접 한 건에 하나(다시 만들면 덮어쓴다). 성공했을 때만 행이 생긴다. */
@Entity
@Table(name = "IV_AI_EVALUATION", uniqueConstraints = @UniqueConstraint(name = "UK_IV_AI_EVAL_INTERVIEW", columnNames = "INTERVIEW_ID"))
@Getter
@Setter
@NoArgsConstructor
public class AiEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivAiEvaluationSeq")
    @SequenceGenerator(name = "ivAiEvaluationSeq", sequenceName = "IV_AI_EVALUATION_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "INTERVIEW_ID", nullable = false)
    private Long interviewId;

    @Column(name = "MODEL_NAME", length = 100)
    private String model;

    @Column(name = "GENERATED_AT")
    private LocalDateTime generatedAt;

    /** [ { criterionCode, criterionName, score, maxScore, rationale, evidenceSeqs } ] — API 명세 2.8 의 scores 모양 그대로. */
    @Lob
    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "SCORES_JSON")
    private JsonNode scores;

    @Lob
    @Column(name = "OVERALL_COMMENT")
    private String overallComment;
}
