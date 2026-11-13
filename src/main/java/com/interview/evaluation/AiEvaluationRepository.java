package com.interview.evaluation;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiEvaluationRepository extends JpaRepository<AiEvaluation, Long> {

    Optional<AiEvaluation> findByInterviewId(Long interviewId);
}
