package com.interview.briefing;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BriefingRepository extends JpaRepository<Briefing, Long> {

    Optional<Briefing> findByInterviewId(Long interviewId);

    List<Briefing> findByInterviewIdIn(Collection<Long> interviewIds);
}
