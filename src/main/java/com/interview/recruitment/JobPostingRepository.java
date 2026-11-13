package com.interview.recruitment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    List<JobPosting> findByRecruitmentIdOrderByJobCodeAsc(Long recruitmentId);

    Optional<JobPosting> findByRecruitmentIdAndJobCode(Long recruitmentId, String jobCode);
}
