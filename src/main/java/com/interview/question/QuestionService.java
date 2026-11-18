package com.interview.question;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interview.auth.CurrentUser;
import com.interview.common.ApiException;
import com.interview.common.Times;
import com.interview.interview.InterviewAccess;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionService {

    /** 브리핑이 면접 전에 만든 예상 꼬리질문의 분류. 면접 중에 채택한 꼬리질문("꼬리질문")과 구분한다. */
    public static final String PREDICTED_FOLLOW_UP_CATEGORY = "예상 꼬리질문";

    private final QuestionRepository questionRepository;
    private final InterviewAccess interviewAccess;

    @Transactional(readOnly = true)
    public List<Question> list(Long interviewId) {
        return questionRepository.findByInterviewIdOrderByOrderNoAscIdAsc(interviewId);
    }

    public Question get(Long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> ApiException.notFound("QUESTION_NOT_FOUND", "질문을 찾을 수 없습니다."));
    }

    @Transactional
    public Question addManual(Long interviewId, QuestionRequests.Add request, CurrentUser user) {
        interviewAccess.requireWritable(interviewId, user);
        return append(interviewId, QuestionSource.MANUAL, request.getCategory(), request.getText().trim(), null, null);
    }

    @Transactional
    public Question patch(Long questionId, QuestionRequests.Patch request, CurrentUser user) {
        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> ApiException.notFound("QUESTION_NOT_FOUND", "질문을 찾을 수 없습니다."));
        interviewAccess.requireWritable(q.getInterviewId(), user);
        if (request.getText() != null) {
            if (request.getText().trim().isEmpty()) {
                throw ApiException.validation("질문 내용은 비울 수 없습니다.");
            }
            q.setText(request.getText().trim());
        }
        if (request.getOrderNo() != null) {
            q.setOrderNo(request.getOrderNo());
        }
        if (request.getAsked() != null) {
            q.setAsked(request.getAsked());
            if (!request.getAsked()) {
                q.setAskedAtSeq(null);
            }
        }
        if (request.getHidden() != null) {
            q.setHidden(request.getHidden());
        }
        return q;
    }

    /** 맨 뒤에 질문 하나를 붙인다(수동 추가, 꼬리질문 채택). */
    @Transactional
    public Question append(Long interviewId, QuestionSource source, String category, String text, String intent,
            Long parentQuestionId) {
        int maxOrder = 0;
        for (Question existing : list(interviewId)) {
            maxOrder = Math.max(maxOrder, existing.getOrderNo());
        }
        Question q = new Question();
        q.setInterviewId(interviewId);
        q.setSource(source);
        q.setCategory(category == null || category.trim().isEmpty() ? null : category.trim());
        q.setText(text);
        q.setIntent(intent);
        q.setOrderNo(maxOrder + 1);
        q.setParentQuestionId(parentQuestionId);
        q.setCreatedAt(Times.now());
        return questionRepository.save(q);
    }

    /** 브리핑이 만든 질문 한 건과, 그 질문에 딸린 예상 꼬리질문의 글(없으면 빈 목록). */
    public static class Generated {
        private final Question question;
        private final List<String> followUps;

        public Generated(Question question, List<String> followUps) {
            this.question = question;
            this.followUps = followUps == null ? new ArrayList<String>() : followUps;
        }
    }

    /**
     * 브리핑이 면접 전에 만든 예상 꼬리질문인지: source 가 FOLLOW_UP 이고 category 가 "예상 꼬리질문"이다.
     * 부모 질문(parentQuestionId)은 보지 않는다(이미 물은 예상 꼬리질문은 부모가 지워져 부모 없이 남을 수 있다).
     */
    public static boolean isPredictedFollowUp(Question q) {
        return q.getSource() == QuestionSource.FOLLOW_UP && PREDICTED_FOLLOW_UP_CATEGORY.equals(q.getCategory());
    }

    /**
     * 브리핑을 다시 만들 때: 브리핑이 만든 것(JD·RESUME·BANK 질문과 예상 꼬리질문) 중 아직 묻지 않은 것을 지우고 새것으로 바꾼다.
     * 이미 물은 것, 수동 질문, 면접 중에 채택한 꼬리질문은 남긴다.
     * 순서: 새 질문(1부터, Agent 가 준 순서) → 남긴 질문 → 새 예상 꼬리질문(부모 질문의 순서대로).
     * 예상 꼬리질문은 source = FOLLOW_UP, parentQuestionId = 그 질문, category = "예상 꼬리질문"으로 저장한다.
     */
    @Transactional
    public void replaceGenerated(Long interviewId, List<Generated> generated) {
        List<Question> keep = new ArrayList<Question>();
        Set<Long> deletedIds = new HashSet<Long>();
        for (Question existing : list(interviewId)) {
            boolean fromBriefing = existing.getSource() == QuestionSource.JD
                    || existing.getSource() == QuestionSource.RESUME || existing.getSource() == QuestionSource.BANK
                    || isPredictedFollowUp(existing);
            if (fromBriefing && !existing.isAsked()) {
                deletedIds.add(existing.getId());
                questionRepository.delete(existing);
            } else {
                keep.add(existing);
            }
        }
        int order = 0;
        for (Generated g : generated) {
            Question q = g.question;
            q.setInterviewId(interviewId);
            q.setOrderNo(++order);
            q.setCreatedAt(Times.now());
            questionRepository.save(q);
        }
        for (Question q : keep) {
            q.setOrderNo(++order);
            // 부모 질문이 지워졌으면 부모 없이 남긴다(이미 물은 예상 꼬리질문).
            if (q.getParentQuestionId() != null && deletedIds.contains(q.getParentQuestionId())) {
                q.setParentQuestionId(null);
            }
        }
        for (Generated g : generated) {
            for (String text : g.followUps) {
                if (text == null || text.trim().isEmpty()) {
                    continue;
                }
                String trimmed = text.trim();
                Question child = new Question();
                child.setInterviewId(interviewId);
                child.setSource(QuestionSource.FOLLOW_UP);
                child.setCategory(PREDICTED_FOLLOW_UP_CATEGORY);
                child.setText(trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000));
                child.setOrderNo(++order);
                child.setParentQuestionId(g.question.getId());
                child.setCreatedAt(Times.now());
                questionRepository.save(child);
            }
        }
    }

    /** Agent 가 "이 발화가 이 질문이다"라고 알려 왔을 때. 다른 면접의 질문이면 무시한다. */
    @Transactional
    public void markAsked(Long interviewId, Long questionId, Integer seq) {
        if (questionId == null) {
            return;
        }
        Question q = questionRepository.findById(questionId).orElse(null);
        if (q == null || !q.getInterviewId().equals(interviewId)) {
            return;
        }
        q.setAsked(true);
        if (seq != null) {
            q.setAskedAtSeq(seq);
        }
    }
}
