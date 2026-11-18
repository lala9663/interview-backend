package com.interview.question;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionFeedbackRepository extends JpaRepository<QuestionFeedback, Long> {

    Optional<QuestionFeedback> findByInterviewIdAndUserId(Long interviewId, Long userId);
}
