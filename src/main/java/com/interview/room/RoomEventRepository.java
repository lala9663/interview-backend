package com.interview.room;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomEventRepository extends JpaRepository<RoomEvent, Long> {

    List<RoomEvent> findByRoomIdOrderByIdAsc(Long roomId);
}
