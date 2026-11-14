package com.interview.evaluation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewerEvaluationRepository extends JpaRepository<InterviewerEvaluation, Long> {

    Optional<InterviewerEvaluation> findByInterviewIdAndUserId(Long interviewId, Long userId);

    List<InterviewerEvaluation> findByInterviewId(Long interviewId);

    List<InterviewerEvaluation> findByUserIdAndInterviewIdIn(Long userId, Collection<Long> interviewIds);

    /** 채용 전체의 제출된 평가. */
    @Query("select e from InterviewerEvaluation e where e.submitted = true and e.interviewId in (select i.id from Interview i where i.recruitmentId = :rid)")
    List<InterviewerEvaluation> findSubmittedByRecruitmentId(@Param("rid") Long recruitmentId);
}
