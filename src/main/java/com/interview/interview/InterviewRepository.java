package com.interview.interview;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InterviewRepository extends JpaRepository<Interview, Long>, JpaSpecificationExecutor<Interview> {

    long countByRecruitmentId(Long recruitmentId);

    long countByRecruitmentIdAndStatus(Long recruitmentId, InterviewStatus status);

    List<Interview> findByCandidateIdOrderBySeqAsc(Long candidateId);

    List<Interview> findByRecruitmentIdOrderByCandidateIdAscSeqAsc(Long recruitmentId);

    List<Interview> findByStatusIn(Collection<InterviewStatus> statuses);
}
