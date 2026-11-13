package com.interview.evaluation;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.job.JobStatus;

import lombok.Getter;
import lombok.Setter;

/** 계약 2.8 의 평가 관련 요청·응답. */
public final class EvaluationDtos {

    private EvaluationDtos() {
    }

    @Getter
    public static class CriterionDto {
        private final String code;
        private final String name;
        private final String description;
        private final int maxScore;

        public CriterionDto(EvalCriterion c) {
            this.code = c.getCode();
            this.name = c.getName();
            this.description = c.getDescription();
            this.maxScore = c.getMaxScore();
        }
    }

    @Getter
    @Setter
    public static class ScoreInput {
        @NotBlank
        private String criterionCode;
        private Integer score;
        @Size(max = 1000)
        private String comment;
    }

    /** PUT /interviews/{id}/my-evaluation. null 인 필드는 바꾸지 않는다(자동 저장에서 일부만 보내도 된다). */
    @Getter
    @Setter
    public static class MyEvaluationRequest {
        private String memo;
        @Valid
        private List<ScoreInput> scores;
        @Size(max = 20)
        private String grade;
        private Boolean submitted;
    }

    @Getter
    @Setter
    public static class MyEvaluation {
        private Long interviewId;
        private String memo = "";
        private JsonNode scores;
        private String grade;
        private boolean submitted;
        private OffsetDateTime updatedAt;
        private OffsetDateTime submittedAt;
    }

    /** AI 참고 평가. 아직 없으면 status 는 가장 최근 작업의 상태(없으면 null)이고 scores 는 빈 배열이다. */
    @Getter
    @Setter
    public static class AiEvaluationDto {
        private JobStatus status;
        private String model;
        private OffsetDateTime generatedAt;
        private Object scores = new ArrayList<Object>();
        private String overallComment;
    }
}
