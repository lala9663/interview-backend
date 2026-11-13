package com.interview.room;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {

    List<FollowUp> findByRoomIdOrderByIdAsc(Long roomId);
}
