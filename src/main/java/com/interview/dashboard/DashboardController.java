package com.interview.dashboard;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.interview.auth.CurrentUser;

import lombok.RequiredArgsConstructor;

/** API 명세 2.9 의 대시보드. */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public DashboardSummary summary(@RequestParam(required = false) Long recruitmentId,
            @AuthenticationPrincipal CurrentUser user) {
        return dashboardService.summary(recruitmentId, user);
    }
}
