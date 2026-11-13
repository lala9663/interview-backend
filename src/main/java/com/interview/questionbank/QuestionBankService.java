package com.interview.questionbank;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.criteria.Predicate;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interview.common.ApiException;
import com.interview.common.Times;
import com.interview.interview.InterviewType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionBankService {

    private final QuestionBankRepository repository;

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
