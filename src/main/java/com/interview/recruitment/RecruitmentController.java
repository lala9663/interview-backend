package com.interview.recruitment;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.interview.common.web.ListResponse;

import lombok.RequiredArgsConstructor;

/** API 명세 2.2 채용(공고)과 직무. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecruitmentController {

    private final RecruitmentService recruitmentService;

    @GetMapping("/recruitments")
    public ListResponse<RecruitmentDto> list(@RequestParam(required = false) Track track) {
        List<RecruitmentDto> items = new ArrayList<RecruitmentDto>();
        for (Recruitment r : recruitmentService.list(track)) {
            items.add(RecruitmentDto.from(r));
        }
        return ListResponse.of(items);
    }

    @PostMapping("/recruitments")
    @PreAuthorize("hasRole('ADMIN')")
    public RecruitmentDto create(@Valid @RequestBody RecruitmentRequest request) {
        return RecruitmentDto.from(recruitmentService.create(request));
    }

    @GetMapping("/recruitments/{id}")
    public RecruitmentDto detail(@PathVariable Long id) {
        return recruitmentService.detail(id);
    }

    @GetMapping("/recruitments/{id}/jobs")
    public ListResponse<JobPostingDto> jobs(@PathVariable Long id) {
        List<JobPostingDto> items = new ArrayList<JobPostingDto>();
        for (JobPosting j : recruitmentService.jobs(id)) {
            items.add(JobPostingDto.from(j));
        }
        return ListResponse.of(items);
    }

    @PostMapping("/recruitments/{id}/jobs")
    @PreAuthorize("hasRole('ADMIN')")
    public JobPostingDto addJob(@PathVariable Long id, @Valid @RequestBody JobPostingRequest request) {
        return JobPostingDto.from(recruitmentService.addJob(id, request));
    }

    /** JD 수정. (AI 작업 조회는 GET /ai-jobs/{jobId}, job 패키지) */
    @PutMapping("/jobs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public JobPostingDto updateJob(@PathVariable Long id, @Valid @RequestBody JobPostingRequest request) {
        return JobPostingDto.from(recruitmentService.updateJob(id, request));
    }
}
