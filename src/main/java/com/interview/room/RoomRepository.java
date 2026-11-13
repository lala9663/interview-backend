package com.interview.room;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByInterviewId(Long interviewId);

    List<Room> findByInterviewIdIn(Collection<Long> interviewIds);

    List<Room> findByStatus(RoomStatus status);
}
