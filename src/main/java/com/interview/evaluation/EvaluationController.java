package com.interview.evaluation;

import javax.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interview.auth.CurrentUser;
import com.interview.common.web.ListResponse;

import lombok.RequiredArgsConstructor;

/** 계약 2.8 의 평가. */
@RestController
@RequestMapping("/api/v1/interviews/{id}")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final AiEvaluationService aiEvaluationService;

    @GetMapping("/criteria")
    public ListResponse<EvaluationDtos.CriterionDto> criteria(@PathVariable Long id) {
        return ListResponse.of(evaluationService.criteriaDtos(id));
    }

    @GetMapping("/my-evaluation")
    public EvaluationDtos.MyEvaluation getMine(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        return evaluationService.getMine(id, user);
    }

    @PutMapping("/my-evaluation")
    public EvaluationDtos.MyEvaluation saveMine(@PathVariable Long id,
            @Valid @RequestBody EvaluationDtos.MyEvaluationRequest request, @AuthenticationPrincipal CurrentUser user) {
        return evaluationService.saveMine(id, request, user);
    }

    @GetMapping("/ai-evaluation")
    public EvaluationDtos.AiEvaluationDto aiEvaluation(@PathVariable Long id) {
        return aiEvaluationService.get(id);
    }
}
