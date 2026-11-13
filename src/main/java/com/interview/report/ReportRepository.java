package com.interview.report;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByInterviewIdOrderByVersionDesc(Long interviewId);

    Optional<Report> findFirstByInterviewIdOrderByVersionDesc(Long interviewId);

    List<Report> findByInterviewIdIn(Collection<Long> interviewIds);
}
