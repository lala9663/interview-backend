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

    @Query("select r from Room r where r.interviewId in (select i.id from Interview i where i.recruitmentId = :rid)")
    List<Room> findByRecruitmentId(@Param("rid") Long recruitmentId);
}
