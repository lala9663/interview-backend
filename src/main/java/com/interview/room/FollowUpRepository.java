package com.interview.room;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {

    List<FollowUp> findByRoomIdOrderByIdAsc(Long roomId);

    /** 채용 전체의 꼬리질문 집계: [제안 수, 채택 수, 제안이 있었던 방 수] 한 행. */
    @Query("select count(f), sum(case when f.used = true then 1 else 0 end), count(distinct f.roomId) from FollowUp f "
            + "where f.roomId in (select r.id from Room r where r.interviewId in (select i.id from Interview i where i.recruitmentId = :rid))")
    List<Object[]> summarizeOfRecruitment(@Param("rid") Long recruitmentId);
}
