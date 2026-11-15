package com.interview.report;

import java.util.Collections;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interview.auth.CurrentUser;
import com.interview.common.web.ListResponse;

import lombok.RequiredArgsConstructor;

/** API 명세 2.8 의 보고서. */
@RestController
@RequestMapping("/api/v1/interviews/{id}/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public ReportDto latest(@PathVariable Long id) {
        return reportService.latest(id);
    }

    @GetMapping("/versions")
    public ListResponse<ReportDto> versions(@PathVariable Long id) {
        return ListResponse.of(reportService.versions(id));
    }

    @PostMapping("/regenerate")
    public ResponseEntity<Map<String, Object>> regenerate(@PathVariable Long id,
            @AuthenticationPrincipal CurrentUser user) {
        Long jobId = reportService.regenerate(id, user).getId();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Collections.<String, Object>singletonMap("jobId", jobId));
    }
}
