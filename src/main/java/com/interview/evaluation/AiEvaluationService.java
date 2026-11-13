package com.interview.evaluation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interview.agent.dto.AgentDtos;
import com.interview.briefing.BriefingService;
import com.interview.common.Times;
import com.interview.common.json.Json;
import com.interview.interview.InterviewAccess;
import com.interview.interview.InterviewContext;
import com.interview.job.AiJobService;
import com.interview.job.JobStatus;
import com.interview.job.JobType;
import com.interview.room.TranscriptService;

import lombok.RequiredArgsConstructor;

/** AI 참고 평가의 조회, Agent 요청 만들기, 결과 저장. */
@Service
@RequiredArgsConstructor
public class AiEvaluationService {

    private final AiEvaluationRepository aiEvaluationRepository;
    private final EvaluationService evaluationService;
    private final InterviewAccess interviewAccess;
    private final InterviewContext.Loader contextLoader;
    private final TranscriptService transcriptService;
    private final BriefingService briefingService;
    private final AiJobService jobService;

    @Transactional(readOnly = true)
    public EvaluationDtos.AiEvaluationDto get(Long interviewId) {
        interviewAccess.requireExisting(interviewId);
        EvaluationDtos.AiEvaluationDto dto = new EvaluationDtos.AiEvaluationDto();
        JobStatus jobStatus = jobService.latestStatus(interviewId, JobType.AI_EVALUATION);
        AiEvaluation e = aiEvaluationRepository.findByInterviewId(interviewId).orElse(null);
        if (e == null) {
            dto.setStatus(jobStatus);
            return dto;
        }
        // 다시 만드는 중이면 그 상태를 보여 주되, 이전 결과는 그대로 둔다.
        dto.setStatus(jobStatus == JobStatus.PENDING || jobStatus == JobStatus.RUNNING ? jobStatus : JobStatus.SUCCEEDED);
        dto.setModel(e.getModel());
        dto.setGeneratedAt(Times.toOffset(e.getGeneratedAt()));
        if (e.getScores() != null) {
            dto.setScores(e.getScores());
        }
        dto.setOverallComment(e.getOverallComment());
        return dto;
    }

    @Transactional(readOnly = true)
    public AgentDtos.EvaluationRequest buildRequest(Long interviewId) {
        InterviewContext ctx = contextLoader.load(interviewId);
        AgentDtos.EvaluationRequest request = new AgentDtos.EvaluationRequest();
        request.setTrack(ctx.getInterview().getTrack());
        request.setInterviewType(ctx.getInterview().getType());
        request.setJobTitle(ctx.getJobTitle());
        request.setJdText(ctx.getJdText());
        for (EvalCriterion c : evaluationService.criteria(interviewId)) {
            request.getCriteria().add(new AgentDtos.CriterionItem(c.getCode(), c.getName(), c.getDescription(), c.getMaxScore()));
        }
        request.setTranscript(transcriptService.linesOfInterview(interviewId));
        JsonNode summary = briefingService.summaryOf(interviewId);
        request.setBriefingSummary(summary == null ? Json.object() : summary);
        return request;
    }

    @Transactional
    public void saveResult(Long interviewId, AgentDtos.EvaluationResponse response) {
        Map<String, EvalCriterion> criteria = new HashMap<String, EvalCriterion>();
        for (EvalCriterion c : evaluationService.criteria(interviewId)) {
            criteria.put(c.getCode(), c);
        }
        ArrayNode scores = Json.array();
        if (response.getScores() != null) {
            for (AgentDtos.ScoreItem s : response.getScores()) {
                EvalCriterion c = criteria.get(s.getCriterionCode());
                if (c == null) {
                    continue; // 요청하지 않은 항목은 버린다.
                }
                ObjectNode node = scores.addObject();
                node.put("criterionCode", c.getCode());
                node.put("criterionName", c.getName());
                if (s.getScore() == null) {
                    node.putNull("score");
                } else {
                    node.put("score", Math.max(0, Math.min(c.getMaxScore(), s.getScore())));
                }
                node.put("maxScore", c.getMaxScore());
                node.put("rationale", s.getRationale() == null ? "" : s.getRationale());
                ArrayNode seqs = node.putArray("evidenceSeqs");
                if (s.getEvidenceSeqs() != null) {
                    for (Integer seq : s.getEvidenceSeqs()) {
                        if (seq != null) {
                            seqs.add(seq);
                        }
                    }
                }
            }
        }
        AiEvaluation e = aiEvaluationRepository.findByInterviewId(interviewId).orElse(null);
        if (e == null) {
            e = new AiEvaluation();
            e.setInterviewId(interviewId);
        }
        e.setModel(response.getModel());
        e.setGeneratedAt(Times.now());
        e.setScores(scores);
        e.setOverallComment(response.getOverallComment());
        aiEvaluationRepository.save(e);
    }

    /** 보고서 요청에 넣을 AI 평가 부분. 없으면 빈 값. */
    @Transactional(readOnly = true)
    public AgentDtos.AiEvaluationPart partForReport(Long interviewId) {
        AgentDtos.AiEvaluationPart part = new AgentDtos.AiEvaluationPart();
        AiEvaluation e = aiEvaluationRepository.findByInterviewId(interviewId).orElse(null);
        if (e == null) {
            return part;
        }
        part.setOverallComment(e.getOverallComment() == null ? "" : e.getOverallComment());
        if (e.getScores() != null) {
            for (JsonNode node : e.getScores()) {
                AgentDtos.ScoreItem item = new AgentDtos.ScoreItem();
                item.setCriterionCode(node.path("criterionCode").asText());
                item.setScore(node.hasNonNull("score") ? node.get("score").asInt() : null);
                item.setRationale(node.path("rationale").asText(""));
                List<Integer> seqs = item.getEvidenceSeqs();
                for (JsonNode seq : node.path("evidenceSeqs")) {
                    seqs.add(seq.asInt());
                }
                part.getScores().add(item);
            }
        }
        return part;
    }
}
