package com.interview.briefing;

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

import lombok.RequiredArgsConstructor;

/** API 명세 2.6 의 사전 리포트(브리핑). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BriefingController {

    private final BriefingService briefingService;

    @GetMapping("/interviews/{id}/briefing")
    public BriefingDto get(@PathVariable Long id) {
        return briefingService.get(id);
    }

    @PostMapping("/interviews/{id}/briefing/generate")
    public ResponseEntity<Map<String, Object>> generate(@PathVariable Long id,
            @AuthenticationPrincipal CurrentUser user) {
        Long jobId = briefingService.requestGenerate(id, user).getId();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Collections.<String, Object>singletonMap("jobId", jobId));
    }
}
