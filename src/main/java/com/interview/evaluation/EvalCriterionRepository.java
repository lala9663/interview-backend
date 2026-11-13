package com.interview.evaluation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.interview.interview.InterviewType;
import com.interview.recruitment.Track;

public interface EvalCriterionRepository extends JpaRepository<EvalCriterion, Long> {

    List<EvalCriterion> findByTrackAndInterviewTypeOrderByOrderNoAsc(Track track, InterviewType interviewType);
}
