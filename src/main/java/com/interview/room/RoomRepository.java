package com.interview.room;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByInterviewId(Long interviewId);

    List<Room> findByInterviewIdIn(Collection<Long> interviewIds);

    List<Room> findByStatus(RoomStatus status);

    /** 주어진 기간에 시작한 방들의 시작 시각(채용 구분 없이 전체). 시스템 상태의 일별 면접 세션에 쓴다. */
    @Query("select r.startedAt from Room r where r.startedAt >= :from and r.startedAt < :to")
    List<java.time.LocalDateTime> findStartedAtBetween(@Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);

    @Query("select r from Room r where r.interviewId in (select i.id from Interview i where i.recruitmentId = :rid)")
    List<Room> findByRecruitmentId(@Param("rid") Long recruitmentId);
}
