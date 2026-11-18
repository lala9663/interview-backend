package com.interview.job;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiJobRepository extends JpaRepository<AiJob, Long> {

    List<AiJob> findByInterviewIdOrderByIdDesc(Long interviewId);

    List<AiJob> findByInterviewIdInAndType(Collection<Long> interviewIds, JobType type);

    Optional<AiJob> findFirstByInterviewIdAndTypeOrderByIdDesc(Long interviewId, JobType type);

    List<AiJob> findByStatusInAndWorkerId(Collection<JobStatus> statuses, String workerId);

    /** 그 종류에서 가장 최근에 끝난 작업 50건(끝난 시각이 늦은 것 먼저). 시스템 상태의 "기다리는 시간"에 쓴다. */
    List<AiJob> findTop50ByTypeAndStatusAndFinishedAtIsNotNullOrderByFinishedAtDescIdDesc(JobType type, JobStatus status);

    long countByTypeAndStatus(JobType type, JobStatus status);

    /** 채용 전체의 작업 집계: [type, status, 건수]. */
    @Query("select j.type, j.status, count(j) from AiJob j where j.interviewId in (select i.id from Interview i where i.recruitmentId = :rid) group by j.type, j.status")
    List<Object[]> countByTypeAndStatusOfRecruitment(@Param("rid") Long recruitmentId);

    /** 면접마다 주어진 유형 가운데 가장 최근 작업 하나. */
    @Query("select j from AiJob j where j.id in (select max(x.id) from AiJob x "
            + "where x.interviewId in :interviewIds and x.type in :types group by x.interviewId)")
    List<AiJob> findLatestPerInterview(@Param("interviewIds") Collection<Long> interviewIds,
            @Param("types") Collection<JobType> types);
}
