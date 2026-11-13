package com.interview.recruitment;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interview.candidate.CandidateRepository;
import com.interview.common.ApiException;
import com.interview.common.Times;
import com.interview.interview.InterviewRepository;
import com.interview.interview.InterviewStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecruitmentService {

    private final RecruitmentRepository recruitmentRepository;
    private final JobPostingRepository jobPostingRepository;
    private final CandidateRepository candidateRepository;
    private final InterviewRepository interviewRepository;

    public List<Recruitment> list(Track track) {
        return track == null ? recruitmentRepository.findAllByOrderByIdAsc()
                : recruitmentRepository.findByTrackOrderByIdAsc(track);
    }

    public Recruitment get(Long id) {
        return recruitmentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("RECRUITMENT_NOT_FOUND", "채용을 찾을 수 없습니다."));
    }

    public RecruitmentDto detail(Long id) {
        RecruitmentDto dto = RecruitmentDto.from(get(id));
        dto.setCandidateCount(candidateRepository.countByRecruitmentId(id));
        dto.setInterviewCount(interviewRepository.countByRecruitmentId(id));
        dto.setDoneCount(interviewRepository.countByRecruitmentIdAndStatus(id, InterviewStatus.DONE));
        return dto;
    }

    @Transactional
    public Recruitment create(RecruitmentRequest request) {
        if (request.getStartDate() != null && request.getEndDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {
            throw ApiException.validation("종료일이 시작일보다 빠릅니다.");
        }
        Recruitment r = new Recruitment();
        r.setTitle(request.getTitle().trim());
        r.setTrack(request.getTrack());
        r.setStartDate(request.getStartDate());
        r.setEndDate(request.getEndDate());
        r.setCreatedAt(Times.now());
        return recruitmentRepository.save(r);
    }

    public List<JobPosting> jobs(Long recruitmentId) {
        get(recruitmentId);
        return jobPostingRepository.findByRecruitmentIdOrderByJobCodeAsc(recruitmentId);
    }

    /** 없으면 null. 다른 기능에서 JD 를 읽을 때 쓴다. */
    public JobPosting findJob(Long recruitmentId, String jobCode) {
        return jobPostingRepository.findByRecruitmentIdAndJobCode(recruitmentId, jobCode).orElse(null);
    }

    @Transactional
    public JobPosting addJob(Long recruitmentId, JobPostingRequest request) {
        get(recruitmentId);
        if (isBlank(request.getJobCode()) || isBlank(request.getTitle())) {
            throw ApiException.validation("jobCode 와 title 은 필수입니다.");
        }
        String jobCode = request.getJobCode().trim();
        if (jobPostingRepository.findByRecruitmentIdAndJobCode(recruitmentId, jobCode).isPresent()) {
            throw ApiException.conflict("JOB_ALREADY_EXISTS", "이미 등록된 직무 코드입니다.");
        }
        JobPosting j = new JobPosting();
        j.setRecruitmentId(recruitmentId);
        j.setJobCode(jobCode);
        j.setTitle(request.getTitle().trim());
        j.setJdText(request.getJdText());
        j.setUpdatedAt(Times.now());
        return jobPostingRepository.save(j);
    }

    @Transactional
    public JobPosting updateJob(Long jobId, JobPostingRequest request) {
        JobPosting j = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("JOB_NOT_FOUND", "직무를 찾을 수 없습니다."));
        if (!isBlank(request.getTitle())) {
            j.setTitle(request.getTitle().trim());
        }
        if (request.getJdText() != null) {
            j.setJdText(request.getJdText());
        }
        j.setUpdatedAt(Times.now());
        return j;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
