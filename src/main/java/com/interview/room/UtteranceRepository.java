package com.interview.room;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UtteranceRepository extends JpaRepository<Utterance, Long> {

    List<Utterance> findByRoomIdOrderBySeqAsc(Long roomId);

    List<Utterance> findByRoomIdAndSeqGreaterThanOrderBySeqAsc(Long roomId, int seq);

    List<Utterance> findByRoomIdAndSeqIn(Long roomId, Collection<Integer> seqs);

    boolean existsByRoomIdAndSource(Long roomId, TranscriptSource source);

    @Query("select max(u.seq) from Utterance u where u.roomId = :roomId")
    Integer findMaxSeq(@Param("roomId") Long roomId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Utterance u where u.roomId = :roomId")
    void deleteByRoomId(@Param("roomId") Long roomId);
}
