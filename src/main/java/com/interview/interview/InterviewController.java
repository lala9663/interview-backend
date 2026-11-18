package com.interview.interview;

import java.time.LocalDate;

import javax.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.interview.auth.CurrentUser;
import com.interview.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/** API 명세 2.5 면접(페르소나 고르기 포함), 2.4 의 수동 지정. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @GetMapping("/interviews")
    public PageResponse<InterviewDto> list(@RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) InterviewStatus status,
            @RequestParam(required = false) Long recruitmentId, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @AuthenticationPrincipal CurrentUser user) {
        return interviewService.list(Boolean.TRUE.equals(mine), date, status, recruitmentId, page, size, user);
    }

    @PostMapping("/candidates/{id}/interviews")
    @PreAuthorize("hasRole('ADMIN')")
    public InterviewDto create(@PathVariable Long id, @Valid @RequestBody InterviewRequests.Create request) {
        return interviewService.create(id, request);
    }

    @GetMapping("/interviews/{id}")
    public InterviewDto detail(@PathVariable Long id) {
        return interviewService.detail(id);
    }

    @PatchMapping("/interviews/{id}")
    public InterviewDto patch(@PathVariable Long id, @Valid @RequestBody InterviewRequests.Patch request,
            @AuthenticationPrincipal CurrentUser user) {
        return interviewService.patch(id, request, user);
    }

    @PutMapping("/interviews/{id}/persona")
    public InterviewDto setPersona(@PathVariable Long id, @Valid @RequestBody InterviewRequests.SetPersona request,
            @AuthenticationPrincipal CurrentUser user) {
        return interviewService.setPersona(id, request.getPersonaId(), user);
    }

    @PutMapping("/interviews/{id}/interviewers")
    @PreAuthorize("hasRole('ADMIN')")
    public InterviewDto setInterviewers(@PathVariable Long id,
            @Valid @RequestBody InterviewRequests.SetInterviewers request) {
        return interviewService.setInterviewers(id, request.getInterviewerIds());
    }
}
