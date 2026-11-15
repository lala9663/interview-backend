package com.interview.agent.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.interview.candidate.CandidateProfile;
import com.interview.interview.InterviewType;
import com.interview.recruitment.Track;
import com.interview.report.ReportBasis;
import com.interview.report.ReportDetail;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 백엔드 → Agent 내부 API(API 명세 4장)의 요청·응답 모양.
 * 블라인드 원칙: 여기에는 면접자의 이름·수험번호 필드가 없다. profile 은 블라인드 항목이 빠진 값만 담는다.
 */
public final class AgentDtos {

    private AgentDtos() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankItem {
        private Long id;
        private String category;
        private String text;
    }

    // ---- 4.1 briefings ----

    @Data
    public static class BriefingRequest {
        private Track track;
        private InterviewType interviewType;
        private String jobTitle;
        private String jdText;
        private CandidatePart candidate;
        private List<BankItem> questionBank = new ArrayList<BankItem>();
        private QuestionCount questionCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidatePart {
        private CandidateProfile profile;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionCount {
        private int jd;
        private int resume;
    }

    @Data
    public static class BriefingResponse {
        private JsonNode summary;
        private List<GeneratedQuestion> questions = new ArrayList<GeneratedQuestion>();
        private List<String> excluded = new ArrayList<String>();
        private String model;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeneratedQuestion {
        private String source;
        private String category;
        private String text;
        private String intent;
    }

    // ---- 4.2 evaluations ----

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterionItem {
        private String code;
        private String name;
        private String description;
        private int maxScore;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TranscriptLine {
        private int seq;
        private String speaker;
        private String text;
    }

    @Data
    public static class EvaluationRequest {
        private Track track;
        private InterviewType interviewType;
        private String jobTitle;
        private String jdText;
        private List<CriterionItem> criteria = new ArrayList<CriterionItem>();
        private List<TranscriptLine> transcript = new ArrayList<TranscriptLine>();
        private JsonNode briefingSummary;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreItem {
        private String criterionCode;
        private Integer score;
        private String rationale;
        private List<Integer> evidenceSeqs = new ArrayList<Integer>();
    }

    @Data
    public static class EvaluationResponse {
        private List<ScoreItem> scores = new ArrayList<ScoreItem>();
        private String overallComment;
        private String model;
    }

    // ---- 4.3 reports ----

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiEvaluationPart {
        private List<ScoreItem> scores = new ArrayList<ScoreItem>();
        private String overallComment = "";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviousRound {
        private InterviewType type;
        private String headline;
        private List<String> checkPoints = new ArrayList<String>();
    }

    @Data
    public static class ReportRequest {
        private Track track;
        private ReportDetail detail;
        private ReportBasis basis;
        private InterviewType interviewType;
        private String jobTitle;
        private String jdText;
        private List<TranscriptLine> transcript = new ArrayList<TranscriptLine>();
        private JsonNode briefingSummary;
        private AiEvaluationPart aiEvaluation = new AiEvaluationPart();
        private List<PreviousRound> previousRounds = new ArrayList<PreviousRound>();
    }

    @Data
    public static class ReportResponse {
        private JsonNode content;
        private String model;
    }

    // ---- 4.4 finalize ----

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinalizeResponse {
        private boolean flushed;
        private boolean postSttScheduled;
        private String audioRetention;
    }

    // ---- GET /internal/v1/stats (운영 통계). Agent 가 필드를 늘려도 깨지지 않게 모르는 필드는 무시한다. ----

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatsResponse {
        private String instanceId;
        /** 이 Agent 가 지금 맡고 있는 방 수. */
        private int rooms;
        /** 역할(llm.fast, stt.live ...) → 상태. */
        private Map<String, ProviderStat> providers = new LinkedHashMap<String, ProviderStat>();
        private List<RoomStat> roomStats = new ArrayList<RoomStat>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProviderStat {
        private String impl;
        private long calls;
        private long failures;
        private int waiting;
        private String health;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RoomStat {
        private Long roomId;
        private int queueDepth;
        private int maxQueueDepth;
        private int utterances;
        private int droppedChunks;
        private Boolean audioConnected;
    }
}
