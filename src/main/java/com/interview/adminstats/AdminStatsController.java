package com.interview.adminstats;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** 계약 2.11 관리자 집계. 읽기 전용이고 ADMIN 만 부를 수 있다. recruitmentId 는 필수(없으면 400). */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminStatsController {

    private final OperationsService operationsService;
    private final QualityService qualityService;
    private final InterviewerStatsService interviewerStatsService;

    @GetMapping("/operations")
    @PreAuthorize("hasRole('ADMIN')")
    public OperationsDtos.Response operations(@RequestParam Long recruitmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return operationsService.operations(recruitmentId, date);
    }

    @GetMapping("/quality")
    @PreAuthorize("hasRole('ADMIN')")
    public QualityDtos.Response quality(@RequestParam Long recruitmentId) {
        return qualityService.quality(recruitmentId);
    }

    @GetMapping("/interviewer-stats")
    @PreAuthorize("hasRole('ADMIN')")
    public InterviewerStatsDtos.Response interviewerStats(@RequestParam Long recruitmentId) {
        return interviewerStatsService.stats(recruitmentId);
    }
}
