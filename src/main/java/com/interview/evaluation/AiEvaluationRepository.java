package com.interview.evaluation;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiEvaluationRepository extends JpaRepository<AiEvaluation, Long> {

    Optional<AiEvaluation> findByInterviewId(Long interviewId);

    @Query("select e from AiEvaluation e where e.interviewId in (select i.id from Interview i where i.recruitmentId = :rid)")
    java.util.List<AiEvaluation> findByRecruitmentId(@Param("rid") Long recruitmentId);
}
