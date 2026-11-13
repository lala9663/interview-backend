package com.interview.evaluation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interview.auth.CurrentUser;
import com.interview.common.ApiException;
import com.interview.common.Times;
import com.interview.common.json.Json;
import com.interview.interview.Interview;
import com.interview.interview.InterviewAccess;
import com.interview.interview.InterviewAssignee;
import com.interview.interview.InterviewAssigneeRepository;
import com.interview.interview.InterviewStatus;

import lombok.RequiredArgsConstructor;

/** 면접관 평가(메모·점수)와 평가 항목. */
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final EvalCriterionRepository criterionRepository;
    private final InterviewerEvaluationRepository evaluationRepository;
    private final InterviewAssigneeRepository assigneeRepository;
    private final InterviewAccess interviewAccess;

    @Transactional(readOnly = true)
    public List<EvalCriterion> criteria(Long interviewId) {
        Interview interview = interviewAccess.requireExisting(interviewId);
        return criterionRepository.findByTrackAndInterviewTypeOrderByOrderNoAsc(interview.getTrack(), interview.getType());
    }

    @Transactional(readOnly = true)
    public EvaluationDtos.MyEvaluation getMine(Long interviewId, CurrentUser user) {
        interviewAccess.requireExisting(interviewId);
        return toDto(interviewId, evaluationRepository.findByInterviewIdAndUserId(interviewId, user.getId()).orElse(null));
    }

    /** 내 평가 저장(면접 중 자동 저장 포함). 배정된 면접관과 ADMIN 만 쓸 수 있다. */
    @Transactional
    public EvaluationDtos.MyEvaluation saveMine(Long interviewId, EvaluationDtos.MyEvaluationRequest request,
            CurrentUser user) {
        Interview interview = interviewAccess.requireWritable(interviewId, user);
        InterviewerEvaluation e = evaluationRepository.findByInterviewIdAndUserId(interviewId, user.getId()).orElse(null);
        if (e == null) {
            e = new InterviewerEvaluation();
            e.setInterviewId(interviewId);
            e.setUserId(user.getId());
        }
        if (request.getMemo() != null) {
            e.setMemo(request.getMemo());
        }
        if (request.getScores() != null) {
            Map<String, EvalCriterion> criteria = new HashMap<String, EvalCriterion>();
            for (EvalCriterion c : criterionRepository.findByTrackAndInterviewTypeOrderByOrderNoAsc(interview.getTrack(),
                    interview.getType())) {
                criteria.put(c.getCode(), c);
            }
            ArrayNode scores = Json.array();
            Set<String> seen = new HashSet<String>();
            for (EvaluationDtos.ScoreInput in : request.getScores()) {
                EvalCriterion c = criteria.get(in.getCriterionCode());
                if (c == null) {
                    throw ApiException.validation("이 면접의 평가 항목이 아닙니다: " + in.getCriterionCode());
                }
                if (!seen.add(in.getCriterionCode())) {
                    throw ApiException.validation("평가 항목이 중복됩니다: " + in.getCriterionCode());
                }
                if (in.getScore() != null && (in.getScore() < 0 || in.getScore() > c.getMaxScore())) {
                    throw ApiException.validation(c.getName() + " 점수는 0~" + c.getMaxScore() + " 사이여야 합니다.");
                }
                ObjectNode node = scores.addObject();
                node.put("criterionCode", in.getCriterionCode());
                if (in.getScore() == null) {
                    node.putNull("score");
                } else {
                    node.put("score", in.getScore());
                }
                node.put("comment", in.getComment() == null ? "" : in.getComment());
            }
            e.setScores(scores);
        }
        if (request.getGrade() != null) {
            e.setGrade(request.getGrade().trim().isEmpty() ? null : request.getGrade().trim());
        }
        if (request.getSubmitted() != null) {
            if (request.getSubmitted() && !e.isSubmitted()) {
                e.setSubmittedAt(Times.now());
            }
            if (!request.getSubmitted()) {
                e.setSubmittedAt(null);
            }
            e.setSubmitted(request.getSubmitted());
        }
        e.setUpdatedAt(Times.now());
        evaluationRepository.save(e);
        completeIfAllSubmitted(interviewId);
        return toDto(interviewId, e);
    }

    /**
     * 면접이 EVALUATING 이고 배정된 면접관이 모두 평가를 제출했으면 DONE 으로 바꾼다.
     * (배정된 면접관이 없으면 바꾸지 않는다.)
     */
    @Transactional
    public void completeIfAllSubmitted(Long interviewId) {
        Interview interview = interviewAccess.requireExisting(interviewId);
        if (interview.getStatus() != InterviewStatus.EVALUATING) {
            return;
        }
        List<InterviewAssignee> assignees = assigneeRepository.findByInterviewIdOrderByIdAsc(interviewId);
        if (assignees.isEmpty()) {
            return;
        }
        Set<Long> submitted = new HashSet<Long>();
        for (InterviewerEvaluation e : evaluationRepository.findByInterviewId(interviewId)) {
            if (e.isSubmitted()) {
                submitted.add(e.getUserId());
            }
        }
        for (InterviewAssignee a : assignees) {
            if (!submitted.contains(a.getUserId())) {
                return;
            }
        }
        interview.setStatus(InterviewStatus.DONE);
    }

    /** 주어진 면접들 가운데 이 사용자가 평가를 제출한 면접의 id. */
    @Transactional(readOnly = true)
    public Set<Long> submittedInterviewIds(Long userId, List<Long> interviewIds) {
        Set<Long> result = new HashSet<Long>();
        if (interviewIds.isEmpty()) {
            return result;
        }
        for (List<Long> chunk : com.interview.common.Chunks.of(interviewIds)) {
            for (InterviewerEvaluation e : evaluationRepository.findByUserIdAndInterviewIdIn(userId, chunk)) {
                if (e.isSubmitted()) {
                    result.add(e.getInterviewId());
                }
            }
        }
        return result;
    }

    private EvaluationDtos.MyEvaluation toDto(Long interviewId, InterviewerEvaluation e) {
        EvaluationDtos.MyEvaluation dto = new EvaluationDtos.MyEvaluation();
        dto.setInterviewId(interviewId);
        dto.setScores(Json.array());
        if (e != null) {
            dto.setMemo(e.getMemo() == null ? "" : e.getMemo());
            if (e.getScores() != null) {
                dto.setScores(e.getScores());
            }
            dto.setGrade(e.getGrade());
            dto.setSubmitted(e.isSubmitted());
            dto.setUpdatedAt(Times.toOffset(e.getUpdatedAt()));
            dto.setSubmittedAt(Times.toOffset(e.getSubmittedAt()));
        }
        return dto;
    }

    public List<EvaluationDtos.CriterionDto> criteriaDtos(Long interviewId) {
        List<EvaluationDtos.CriterionDto> items = new ArrayList<EvaluationDtos.CriterionDto>();
        for (EvalCriterion c : criteria(interviewId)) {
            items.add(new EvaluationDtos.CriterionDto(c));
        }
        return items;
    }
}
