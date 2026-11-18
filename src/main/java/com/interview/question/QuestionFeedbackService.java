package com.interview.question;

import java.util.HashSet;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.interview.auth.CurrentUser;
import com.interview.common.ApiException;
import com.interview.common.Times;
import com.interview.common.json.Json;
import com.interview.interview.InterviewAccess;

import lombok.RequiredArgsConstructor;

/** 준비된 질문이 도움이 됐는지에 대한 내 의견(API 명세 2.6). 조회는 로그인한 사용자 모두, 저장은 배정된 면접관 또는 ADMIN. */
@Service
@RequiredArgsConstructor
public class QuestionFeedbackService {

    private final QuestionFeedbackRepository feedbackRepository;
    private final QuestionService questionService;
    private final InterviewAccess interviewAccess;
    private final PlatformTransactionManager transactionManager;

    /** 내 의견. 사전 리포트를 다시 만들어 사라진 질문에 대한 의견은 빼고 돌려준다(저장된 값은 그대로 둔다). */
    public QuestionFeedbackDtos.Feedback getMine(final Long interviewId, final CurrentUser user) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        return tx.execute(status -> {
            interviewAccess.requireExisting(interviewId);
            return toDto(interviewId, feedbackRepository.findByInterviewIdAndUserId(interviewId, user.getId()).orElse(null),
                    questionIdsOf(interviewId));
        });
    }

    /**
     * 내 의견을 통째로 바꿔 저장한다. 이 면접의 질문이 아닌 questionId, 같은 질문이 두 번, 빈(null) 항목이 오면 400.
     * 첫 저장이 동시에 두 번 오면 한쪽이 유니크 제약(면접 + 사용자)에 걸린다. 그때는 한 번 더 해서 이미 생긴 행을 덮어쓴다.
     */
    public QuestionFeedbackDtos.Feedback saveMine(final Long interviewId, final QuestionFeedbackDtos.SaveRequest request,
            final CurrentUser user) {
        final TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            return tx.execute(status -> save(interviewId, request, user));
        } catch (DataIntegrityViolationException e) {
            return tx.execute(status -> save(interviewId, request, user));
        }
    }

    private QuestionFeedbackDtos.Feedback save(Long interviewId, QuestionFeedbackDtos.SaveRequest request, CurrentUser user) {
        interviewAccess.requireWritable(interviewId, user);
        Set<Long> questionIds = questionIdsOf(interviewId);
        ArrayNode votes = Json.array();
        Set<Long> seen = new HashSet<Long>();
        if (request.getVotes() != null) {
            for (QuestionFeedbackDtos.VoteItem vote : request.getVotes()) {
                if (vote == null || vote.getQuestionId() == null || vote.getVote() == null) {
                    throw ApiException.validation("votes 의 항목에는 questionId 와 vote 가 있어야 합니다.");
                }
                if (!questionIds.contains(vote.getQuestionId())) {
                    throw ApiException.validation("이 면접의 질문이 아닙니다: " + vote.getQuestionId());
                }
                if (!seen.add(vote.getQuestionId())) {
                    throw ApiException.validation("같은 질문에 의견이 두 번 들어 있습니다: " + vote.getQuestionId());
                }
                votes.addObject().put("questionId", vote.getQuestionId()).put("vote", vote.getVote().name());
            }
        }
        QuestionFeedback f = feedbackRepository.findByInterviewIdAndUserId(interviewId, user.getId()).orElse(null);
        if (f == null) {
            f = new QuestionFeedback();
            f.setInterviewId(interviewId);
            f.setUserId(user.getId());
        }
        f.setOverall(request.getOverall());
        f.setVotes(votes);
        f.setUpdatedAt(Times.now());
        // 바로 DB 에 써서 유니크 충돌이 이 트랜잭션 안에서 드러나게 한다.
        feedbackRepository.saveAndFlush(f);
        return toDto(interviewId, f, questionIds);
    }

    private Set<Long> questionIdsOf(Long interviewId) {
        Set<Long> ids = new HashSet<Long>();
        for (Question q : questionService.list(interviewId)) {
            ids.add(q.getId());
        }
        return ids;
    }

    private static QuestionFeedbackDtos.Feedback toDto(Long interviewId, QuestionFeedback f, Set<Long> questionIds) {
        QuestionFeedbackDtos.Feedback dto = new QuestionFeedbackDtos.Feedback();
        dto.setInterviewId(interviewId);
        if (f != null) {
            dto.setOverall(f.getOverall());
            if (f.getVotes() != null) {
                for (JsonNode node : f.getVotes()) {
                    long questionId = node.path("questionId").asLong();
                    if (!questionIds.contains(questionId)) {
                        continue; // 지금은 없는 질문
                    }
                    dto.getVotes().add(new QuestionFeedbackDtos.VoteItem(questionId, QuestionVote.valueOf(node.path("vote").asText())));
                }
            }
            dto.setUpdatedAt(Times.toOffset(f.getUpdatedAt()));
        }
        return dto;
    }
}
