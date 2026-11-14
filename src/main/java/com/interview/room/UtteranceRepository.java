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

    /** 방별 전사 요약: [roomId, 건수, 마지막 저장 시각, 가장 큰 endMs]. 발화를 엔티티로 읽지 않는다. */
    @Query("select u.roomId, count(u), max(u.savedAt), max(u.endMs) from Utterance u where u.roomId in :roomIds group by u.roomId")
    List<Object[]> summarizeByRoomIds(@Param("roomIds") Collection<Long> roomIds);

    /** 채용 전체의 화자 판정 집계: [speakerMethod, speaker, 건수]. */
    @Query("select u.speakerMethod, u.speaker, count(u) from Utterance u where u.roomId in (select r.id from Room r where r.interviewId in (select i.id from Interview i where i.recruitmentId = :rid)) "
            + "group by u.speakerMethod, u.speaker")
    List<Object[]> countBySpeakerOfRecruitment(@Param("rid") Long recruitmentId);
}
