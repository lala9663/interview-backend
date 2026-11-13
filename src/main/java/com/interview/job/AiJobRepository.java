package com.interview.job;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiJobRepository extends JpaRepository<AiJob, Long> {

    List<AiJob> findByInterviewIdOrderByIdDesc(Long interviewId);

    List<AiJob> findByInterviewIdInAndType(Collection<Long> interviewIds, JobType type);

    Optional<AiJob> findFirstByInterviewIdAndTypeOrderByIdDesc(Long interviewId, JobType type);

    List<AiJob> findByStatusInAndWorkerId(Collection<JobStatus> statuses, String workerId);
}
