package com.interview.questionbank;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.interview.auth.CurrentUser;
import com.interview.common.web.ListResponse;

import lombok.RequiredArgsConstructor;

/** 계약 2.9 질문 은행. */
@RestController
@RequestMapping("/api/v1/question-bank")
@RequiredArgsConstructor
public class QuestionBankController {

    private final QuestionBankService service;

    @GetMapping
    public ListResponse<QuestionBankDto> search(@RequestParam(required = false) String jobCode,
            @RequestParam(required = false) String category, @RequestParam(required = false) String q) {
        List<QuestionBankDto> items = new ArrayList<QuestionBankDto>();
        for (QuestionBankItem item : service.search(jobCode, category, q)) {
            items.add(QuestionBankDto.from(item));
        }
        return ListResponse.of(items);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public QuestionBankDto add(@Valid @RequestBody QuestionBankDto request) {
        return QuestionBankDto.from(service.add(request));
    }

    /** 면접에서 쓴 질문을 은행에 담는다. 그 면접에 배정된 면접관과 관리자. */
    @PostMapping("/from-question/{questionId}")
    public QuestionBankDto saveFromQuestion(@PathVariable Long questionId,
            @Valid @RequestBody(required = false) SaveFromQuestionRequest request,
            @AuthenticationPrincipal CurrentUser user) {
        return QuestionBankDto.from(service.saveFromQuestion(questionId, request, user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
