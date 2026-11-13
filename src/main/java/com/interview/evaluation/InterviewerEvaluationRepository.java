package com.interview.evaluation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewerEvaluationRepository extends JpaRepository<InterviewerEvaluation, Long> {

    Optional<InterviewerEvaluation> findByInterviewIdAndUserId(Long interviewId, Long userId);

    List<InterviewerEvaluation> findByInterviewId(Long interviewId);

    List<InterviewerEvaluation> findByUserIdAndInterviewIdIn(Long userId, Collection<Long> interviewIds);
}
