package com.interview.job;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interview.common.web.ListResponse;
import com.interview.interview.InterviewAccess;

import lombok.RequiredArgsConstructor;

/** API 명세 2.8 의 작업 조회. (JD 수정 PUT /jobs/{id} 와 뜻이 섞이지 않게 /ai-jobs 를 쓴다.) */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class JobController {

    private final AiJobService jobService;
    private final InterviewAccess interviewAccess;

    @GetMapping("/ai-jobs/{jobId}")
    public JobDto get(@PathVariable Long jobId) {
        return jobService.get(jobId);
    }

    @GetMapping("/interviews/{id}/jobs")
    public ListResponse<JobDto> listByInterview(@PathVariable Long id) {
        interviewAccess.requireExisting(id);
        return ListResponse.of(jobService.listByInterview(id));
    }
}
