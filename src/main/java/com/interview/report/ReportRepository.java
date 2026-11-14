package com.interview.report;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByInterviewIdOrderByVersionDesc(Long interviewId);

    Optional<Report> findFirstByInterviewIdOrderByVersionDesc(Long interviewId);

    List<Report> findByInterviewIdIn(Collection<Long> interviewIds);

    /** 면접·기준별 첫 보고서의 생성 시각: [interviewId, basis, 가장 이른 generatedAt]. 본문(CLOB)은 읽지 않는다. */
    @Query("select r.interviewId, r.basis, min(r.generatedAt) from Report r where r.interviewId in (select i.id from Interview i where i.recruitmentId = :rid) "
            + "group by r.interviewId, r.basis")
    List<Object[]> firstGeneratedOfRecruitment(@Param("rid") Long recruitmentId);
}
