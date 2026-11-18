package com.interview.candidate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.interview.common.web.PageResponse;
import com.interview.interview.InterviewStatus;
import com.interview.interview.InterviewType;
import com.interview.recruitment.RecruitmentService;

import lombok.RequiredArgsConstructor;

/** API 명세 2.3 면접자 목록·상세·진행. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;
    private final RecruitmentService recruitmentService;

    @GetMapping("/recruitments/{id}/candidates")
    public PageResponse<CandidateDtos.ListItem> list(@PathVariable Long id,
            @RequestParam(required = false) String jobCode,
            @RequestParam(required = false) ApplicantType applicantType, @RequestParam(required = false) String q,
            @RequestParam(required = false) InterviewStatus status,
            @RequestParam(required = false) InterviewType type, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        recruitmentService.get(id);
        return candidateService.list(id, jobCode, applicantType, q, status, type, page, size);
    }

    @GetMapping("/candidates/{id}")
    public CandidateDtos.Detail detail(@PathVariable Long id) {
        return candidateService.detail(id);
    }

    @GetMapping("/candidates/{id}/progress")
    public CandidateDtos.Progress progress(@PathVariable Long id) {
        return candidateService.progress(id);
    }
}
