package com.interview.interview;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewAssigneeRepository extends JpaRepository<InterviewAssignee, Long> {

    List<InterviewAssignee> findByInterviewIdOrderByIdAsc(Long interviewId);

    List<InterviewAssignee> findByInterviewIdIn(Collection<Long> interviewIds);

    List<InterviewAssignee> findByUserId(Long userId);

    boolean existsByInterviewIdAndUserId(Long interviewId, Long userId);

    @Query("select a from InterviewAssignee a where a.interviewId in "
            + "(select i.id from Interview i where i.recruitmentId = :recruitmentId)")
    List<InterviewAssignee> findByRecruitmentId(@Param("recruitmentId") Long recruitmentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from InterviewAssignee a where a.interviewId = :interviewId")
    void deleteByInterviewId(@Param("interviewId") Long interviewId);
}
