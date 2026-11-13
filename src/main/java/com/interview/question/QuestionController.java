package com.interview.question;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interview.auth.CurrentUser;
import com.interview.common.web.ListResponse;
import com.interview.interview.InterviewAccess;

import lombok.RequiredArgsConstructor;

/** 계약 2.6 의 질문. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final InterviewAccess interviewAccess;

    @GetMapping("/interviews/{id}/questions")
    public ListResponse<QuestionDto> list(@PathVariable Long id) {
        interviewAccess.requireExisting(id);
        List<QuestionDto> items = new ArrayList<QuestionDto>();
        for (Question q : questionService.list(id)) {
            items.add(QuestionDto.from(q));
        }
        return ListResponse.of(items);
    }

    @PostMapping("/interviews/{id}/questions")
    public QuestionDto add(@PathVariable Long id, @Valid @RequestBody QuestionRequests.Add request,
            @AuthenticationPrincipal CurrentUser user) {
        return QuestionDto.from(questionService.addManual(id, request, user));
    }

    @PatchMapping("/questions/{id}")
    public QuestionDto patch(@PathVariable Long id, @Valid @RequestBody QuestionRequests.Patch request,
            @AuthenticationPrincipal CurrentUser user) {
        return QuestionDto.from(questionService.patch(id, request, user));
    }
}
