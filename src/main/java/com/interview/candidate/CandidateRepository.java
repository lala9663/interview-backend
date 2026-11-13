package com.interview.candidate;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CandidateRepository extends JpaRepository<Candidate, Long>, JpaSpecificationExecutor<Candidate> {

    long countByRecruitmentId(Long recruitmentId);

    Optional<Candidate> findByRecruitmentIdAndApplicantNo(Long recruitmentId, String applicantNo);

    List<Candidate> findByRecruitmentIdOrderByApplicantNoAsc(Long recruitmentId);
}
