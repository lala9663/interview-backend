package com.interview.question;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * 브리핑을 다시 만들 때: 브리핑이 만든 질문(JD·RESUME·BANK) 중 아직 묻지 않은 것을 지우고 새 질문으로 바꾼다.
     * 이미 물은 질문, 수동 질문, 꼬리질문은 남긴다.
     */
    @Transactional
    public void replaceGenerated(Long interviewId, List<Question> generated) {
        List<Question> keep = new ArrayList<Question>();
        for (Question existing : list(interviewId)) {
            boolean fromBriefing = existing.getSource() == QuestionSource.JD
                    || existing.getSource() == QuestionSource.RESUME || existing.getSource() == QuestionSource.BANK;
            if (fromBriefing && !existing.isAsked()) {
                questionRepository.delete(existing);
            } else {
                keep.add(existing);
            }
        }
        int order = 0;
        for (Question q : generated) {
            q.setInterviewId(interviewId);
            q.setOrderNo(++order);
            q.setCreatedAt(Times.now());
            questionRepository.save(q);
        }
        for (Question q : keep) {
            q.setOrderNo(++order);
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
