package com.interview.questionbank;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.criteria.Predicate;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interview.auth.CurrentUser;
import com.interview.candidate.CandidateService;
import com.interview.common.ApiException;
import com.interview.common.Times;
import com.interview.interview.Interview;
import com.interview.interview.InterviewAccess;
import com.interview.interview.InterviewType;
import com.interview.question.Question;
import com.interview.question.QuestionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionBankService {

    private final QuestionBankRepository repository;
    private final QuestionService questionService;
    private final InterviewAccess interviewAccess;
    private final CandidateService candidateService;

    public List<QuestionBankItem> search(final String jobCode, final String category, final String q) {
        Specification<QuestionBankItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<Predicate>();
            if (!isBlank(jobCode)) {
                predicates.add(cb.equal(root.get("jobCode"), jobCode.trim()));
            }
            if (!isBlank(category)) {
                predicates.add(cb.equal(root.get("category"), category.trim()));
            }
            if (!isBlank(q)) {
                predicates.add(cb.like(root.<String>get("text"), "%" + q.trim() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec, Sort.by("id"));
    }

    /** 브리핑·방 context 에 넘길 질문 은행(그 직무·유형 + 공통). */
    public List<QuestionBankItem> usableFor(String jobCode, InterviewType type, int limit) {
        return repository.findUsable(jobCode, type, PageRequest.of(0, limit));
    }

    @Transactional
    public QuestionBankItem add(QuestionBankDto request) {
        QuestionBankItem item = new QuestionBankItem();
        item.setJobCode(isBlank(request.getJobCode()) ? null : request.getJobCode().trim());
        item.setInterviewType(request.getInterviewType());
        item.setCategory(isBlank(request.getCategory()) ? null : request.getCategory().trim());
        item.setText(request.getText().trim());
        item.setSampleAnswer(request.getSampleAnswer());
        item.setCreatedAt(Times.now());
        return repository.save(item);
    }

    /**
     * 면접에서 쓴 질문(채택한 꼬리질문, 직접 추가한 질문 등)을 은행에 담는다. 그 면접에 배정된 면접관과 관리자가 할 수 있다.
     * 직무는 면접자의 지원 직무, 면접 유형은 그 면접의 유형으로 넣는다. 같은 직무에 같은 글이 이미 있으면 새로 만들지 않고 그것을 돌려준다.
     */
    @Transactional
    public QuestionBankItem saveFromQuestion(Long questionId, SaveFromQuestionRequest request, CurrentUser user) {
        Question question = questionService.get(questionId);
        Interview interview = interviewAccess.requireWritable(question.getInterviewId(), user);
        String jobCode = candidateService.get(interview.getCandidateId()).getJobCode();
        String text = request == null || isBlank(request.getText()) ? question.getText() : request.getText();
        text = text.trim();
        if (text.length() > 1000) {
            text = text.substring(0, 1000);
        }
        List<QuestionBankItem> same = repository.findByJobCodeAndText(jobCode, text);
        if (!same.isEmpty()) {
            return same.get(0);
        }
        String category = request == null || isBlank(request.getCategory()) ? question.getCategory() : request.getCategory();
        QuestionBankItem item = new QuestionBankItem();
        item.setJobCode(jobCode);
        item.setInterviewType(interview.getType());
        item.setCategory(isBlank(category) ? null : category.trim());
        item.setText(text);
        item.setCreatedAt(Times.now());
        return repository.save(item);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw ApiException.notFound("QUESTION_BANK_NOT_FOUND", "질문을 찾을 수 없습니다.");
        }
        repository.deleteById(id);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
