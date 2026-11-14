package com.interview.room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interview.agent.dto.AgentDtos;
import com.interview.common.ApiException;
import com.interview.common.Chunks;
import com.interview.question.QuestionService;

import lombok.RequiredArgsConstructor;

/** 전사(Utterance) 저장과 조회. 쓰는 쪽은 Agent 콜백(internal 패키지)뿐이다. */
@Service
@RequiredArgsConstructor
public class TranscriptService {

    private final RoomRepository roomRepository;
    private final UtteranceRepository utteranceRepository;
    private final QuestionService questionService;

    /**
     * 실시간 확정 전사 저장. (roomId, seq)가 같으면 덮어쓴다.
     * 이미 사후 STT 보정본으로 바뀐 방이면 늦게 온 실시간 전사는 버린다(0 반환).
     */
    @Transactional
    public int saveLive(Long roomId, List<UtteranceInput> items) {
        Room room = requireRoom(roomId);
        if (room.getPostSttModel() != null || items.isEmpty()) {
            return 0;
        }
        List<Integer> seqs = new ArrayList<Integer>();
        for (UtteranceInput in : items) {
            seqs.add(in.getSeq());
        }
        Map<Integer, Utterance> existing = new HashMap<Integer, Utterance>();
        for (List<Integer> chunk : Chunks.of(seqs)) {
            for (Utterance u : utteranceRepository.findByRoomIdAndSeqIn(roomId, chunk)) {
                existing.put(u.getSeq(), u);
            }
        }
        for (UtteranceInput in : items) {
            Utterance u = existing.get(in.getSeq());
            if (u == null) {
                u = new Utterance();
                u.setRoomId(roomId);
                u.setSeq(in.getSeq());
                existing.put(in.getSeq(), u);
            }
            apply(u, in, TranscriptSource.LIVE);
            utteranceRepository.save(u);
            questionService.markAsked(room.getInterviewId(), in.getMatchedQuestionId(), in.getSeq());
        }
        return items.size();
    }

    /** 사후 STT 보정 전사로 전체를 바꾼다(source = POST). */
    @Transactional
    public int replaceRefined(Long roomId, String sttModel, List<UtteranceInput> items) {
        Room room = requireRoom(roomId);
        utteranceRepository.deleteByRoomId(roomId);
        // deleteByRoomId 가 영속성 컨텍스트를 비우므로 방을 다시 읽는다.
        room = requireRoom(roomId);
        room.setPostSttModel(sttModel == null || sttModel.trim().isEmpty() ? "unknown" : cut(sttModel, 100));
        for (UtteranceInput in : items) {
            Utterance u = new Utterance();
            u.setRoomId(roomId);
            u.setSeq(in.getSeq());
            apply(u, in, TranscriptSource.POST);
            utteranceRepository.save(u);
            questionService.markAsked(room.getInterviewId(), in.getMatchedQuestionId(), in.getSeq());
        }
        return items.size();
    }

    @Transactional(readOnly = true)
    public List<Utterance> list(Long roomId, Integer afterSeq) {
        requireRoom(roomId);
        return afterSeq == null ? utteranceRepository.findByRoomIdOrderBySeqAsc(roomId)
                : utteranceRepository.findByRoomIdAndSeqGreaterThanOrderBySeqAsc(roomId, afterSeq);
    }

    @Transactional(readOnly = true)
    public int lastSeq(Long roomId) {
        Integer max = utteranceRepository.findMaxSeq(roomId);
        return max == null ? 0 : max;
    }

    /** 면접의 전사를 Agent 요청 모양으로. 방이 없으면 빈 목록. */
    @Transactional(readOnly = true)
    public List<AgentDtos.TranscriptLine> linesOfInterview(Long interviewId) {
        List<AgentDtos.TranscriptLine> lines = new ArrayList<AgentDtos.TranscriptLine>();
        Room room = roomRepository.findByInterviewId(interviewId).orElse(null);
        if (room == null) {
            return lines;
        }
        for (Utterance u : utteranceRepository.findByRoomIdOrderBySeqAsc(room.getId())) {
            lines.add(new AgentDtos.TranscriptLine(u.getSeq(), u.getSpeaker().name(), u.bestText()));
        }
        return lines;
    }

    /** 사후 STT 보정본이 반영된 면접인지. */
    @Transactional(readOnly = true)
    public boolean hasRefined(Long interviewId) {
        Room room = roomRepository.findByInterviewId(interviewId).orElse(null);
        return room != null && room.getPostSttModel() != null;
    }

    private Room requireRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> ApiException.notFound("ROOM_NOT_FOUND", "방을 찾을 수 없습니다."));
    }

    private static void apply(Utterance u, UtteranceInput in, TranscriptSource source) {
        u.setSpeaker(in.getSpeaker() == null ? Speaker.UNKNOWN : in.getSpeaker());
        u.setSpeakerMethod(in.getSpeakerMethod() == null ? SpeakerMethod.NONE : in.getSpeakerMethod());
        u.setRawText(in.getRawText());
        u.setRefinedText(in.getRefinedText());
        u.setStartMs(in.getStartMs());
        u.setEndMs(in.getEndMs());
        u.setMatchedQuestionId(in.getMatchedQuestionId());
        u.setSource(source);
        u.setSavedAt(com.interview.common.Times.now());
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
