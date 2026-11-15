package com.interview.room;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.auth.CurrentUser;
import com.interview.common.ApiException;
import com.interview.common.Times;
import com.interview.evaluation.EvaluationService;
import com.interview.interview.Interview;
import com.interview.interview.InterviewAccess;
import com.interview.interview.InterviewStatus;
import com.interview.job.AiJobService;
import com.interview.job.JobType;
import com.interview.question.Question;
import com.interview.question.QuestionService;
import com.interview.question.QuestionSource;

import lombok.RequiredArgsConstructor;

/** 면접 방의 상태 전이와 입장권. API 명세 2.7. */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final FollowUpRepository followUpRepository;
    private final RoomEventRepository roomEventRepository;
    private final UtteranceRepository utteranceRepository;
    private final TranscriptService transcriptService;
    private final InterviewAccess interviewAccess;
    private final RoomTicketService ticketService;
    private final QuestionService questionService;
    private final AiJobService jobService;
    private final EvaluationService evaluationService;

    /** 방 만들기. 이미 있으면 그 방을 돌려준다. */
    @Transactional
    public RoomDtos.RoomDto createOrGet(Long interviewId, CurrentUser user) {
        Interview interview = interviewAccess.requireWritable(interviewId, user);
        Room room = roomRepository.findByInterviewId(interviewId).orElse(null);
        if (room == null) {
            if (interview.getStatus() == InterviewStatus.CANCELED) {
                throw ApiException.conflict("INTERVIEW_CANCELED", "취소된 면접입니다.");
            }
            room = new Room();
            room.setInterviewId(interviewId);
            room.setStatus(RoomStatus.CREATED);
            room.setCreatedAt(Times.now());
            roomRepository.save(room);
        }
        return toDto(room);
    }

    @Transactional(readOnly = true)
    public RoomDtos.RoomDto get(Long roomId) {
        return toDto(requireRoom(roomId));
    }

    /** CREATED → LIVE, 면접 IN_PROGRESS. */
    @Transactional
    public RoomDtos.RoomDto start(Long roomId, CurrentUser user) {
        Room room = requireRoom(roomId);
        Interview interview = interviewAccess.requireWritable(room.getInterviewId(), user);
        if (room.getStatus() == RoomStatus.LIVE) {
            throw ApiException.conflict("ROOM_ALREADY_LIVE", "이미 진행 중인 방입니다.");
        }
        if (room.getStatus() == RoomStatus.ENDED) {
            throw ApiException.conflict("ROOM_ALREADY_ENDED", "이미 종료된 방입니다.");
        }
        room.setStatus(RoomStatus.LIVE);
        room.setStartedAt(Times.now());
        interview.setStatus(InterviewStatus.IN_PROGRESS);
        return toDto(room);
    }

    /**
     * LIVE → ENDED, 면접 EVALUATING. AI 평가 작업을 접수한다.
     * 그 작업이 Agent finalize → AI 평가 → 보고서(LIVE) 순으로 이어진다(evaluation.AiEvaluationJobHandler).
     */
    @Transactional
    public RoomDtos.RoomDto end(Long roomId, CurrentUser user) {
        Room room = requireRoom(roomId);
        Interview interview = interviewAccess.requireWritable(room.getInterviewId(), user);
        if (room.getStatus() == RoomStatus.ENDED) {
            throw ApiException.conflict("ROOM_ALREADY_ENDED", "이미 종료된 방입니다.");
        }
        if (room.getStatus() != RoomStatus.LIVE) {
            throw ApiException.conflict("ROOM_NOT_LIVE", "시작하지 않은 방입니다.");
        }
        room.setStatus(RoomStatus.ENDED);
        room.setEndedAt(Times.now());
        interview.setStatus(InterviewStatus.EVALUATING);
        // 면접 중에 이미 모두 평가를 제출했다면 바로 DONE 이 된다.
        evaluationService.completeIfAllSubmitted(interview.getId());

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("finalizeRoomId", room.getId());
        payload.put("reportBasis", "LIVE");
        jobService.submit(JobType.AI_EVALUATION, interview.getId(), payload);
        return toDto(room);
    }

    /** 입장권. audio=true 는 마이크를 보내는 단말(방마다 하나, 중복 검사는 Agent 가 한다). */
    @Transactional(readOnly = true)
    public RoomDtos.Ticket ticket(Long roomId, boolean audio, CurrentUser user) {
        Room room = requireRoom(roomId);
        interviewAccess.requireWritable(room.getInterviewId(), user);
        if (room.getStatus() == RoomStatus.ENDED) {
            throw ApiException.conflict("ROOM_ALREADY_ENDED", "이미 종료된 방입니다.");
        }
        return ticketService.issue(roomId, user, audio);
    }

    @Transactional(readOnly = true)
    public List<RoomDtos.UtteranceDto> utterances(Long roomId, Integer afterSeq) {
        List<RoomDtos.UtteranceDto> items = new ArrayList<RoomDtos.UtteranceDto>();
        for (Utterance u : transcriptService.list(roomId, afterSeq)) {
            items.add(new RoomDtos.UtteranceDto(u));
        }
        return items;
    }

    @Transactional(readOnly = true)
    public List<RoomDtos.FollowUpDto> followUps(Long roomId) {
        requireRoom(roomId);
        List<RoomDtos.FollowUpDto> items = new ArrayList<RoomDtos.FollowUpDto>();
        for (FollowUp f : followUpRepository.findByRoomIdOrderByIdAsc(roomId)) {
            items.add(new RoomDtos.FollowUpDto(f));
        }
        return items;
    }

    /** 꼬리질문 후보를 질문으로 채택한다. 이미 채택된 후보면 그때 만든 질문을 돌려준다. */
    @Transactional
    public Question useFollowUp(Long followUpId, CurrentUser user) {
        FollowUp followUp = followUpRepository.findById(followUpId)
                .orElseThrow(() -> ApiException.notFound("FOLLOW_UP_NOT_FOUND", "꼬리질문 후보를 찾을 수 없습니다."));
        Room room = requireRoom(followUp.getRoomId());
        interviewAccess.requireWritable(room.getInterviewId(), user);
        if (followUp.isUsed() && followUp.getQuestionId() != null) {
            for (Question q : questionService.list(room.getInterviewId())) {
                if (q.getId().equals(followUp.getQuestionId())) {
                    return q;
                }
            }
        }
        // 근거가 된 발화가 어떤 질문의 답이었는지 알면 그 질문을 부모로 건다.
        Long parentQuestionId = null;
        if (followUp.getBasedOnSeq() != null) {
            for (Utterance u : utteranceRepository.findByRoomIdOrderBySeqAsc(room.getId())) {
                if (u.getSeq() > followUp.getBasedOnSeq()) {
                    break;
                }
                if (u.getMatchedQuestionId() != null) {
                    parentQuestionId = u.getMatchedQuestionId();
                }
            }
        }
        Question question = questionService.append(room.getInterviewId(), QuestionSource.FOLLOW_UP, "꼬리질문",
                followUp.getText(), followUp.getRationale() == null ? null : cut(followUp.getRationale(), 1000),
                parentQuestionId);
        followUp.setUsed(true);
        followUp.setQuestionId(question.getId());
        return question;
    }

    /** Agent 콜백: 꼬리질문 후보 저장. */
    @Transactional
    public int saveFollowUps(Long roomId, Integer basedOnSeq, List<FollowUpInput> items) {
        requireRoom(roomId);
        for (FollowUpInput in : items) {
            FollowUp f = new FollowUp();
            f.setRoomId(roomId);
            f.setBasedOnSeq(basedOnSeq);
            f.setClientId(in.getClientId());
            f.setText(cut(in.getText(), 1000));
            f.setRationale(in.getRationale() == null ? null : cut(in.getRationale(), 1000));
            f.setCreatedAt(Times.now());
            followUpRepository.save(f);
        }
        return items.size();
    }

    /** 방에서 일어난 일을 기록한다(Agent 콜백의 events, 백엔드의 FINALIZE_FAILED). */
    @Transactional
    public void recordEvent(Long roomId, String type, String message, JsonNode data) {
        Room room = requireRoom(roomId);
        RoomEvent event = new RoomEvent();
        event.setRoomId(roomId);
        event.setType(cut(type, 50));
        event.setMessage(message == null ? null : cut(message, 1000));
        event.setData(data);
        event.setCreatedAt(Times.now());
        roomEventRepository.save(event);
        if ("QUESTION_ASKED".equals(type) && data != null && data.hasNonNull("questionId")) {
            questionService.markAsked(room.getInterviewId(), data.get("questionId").asLong(),
                    data.hasNonNull("seq") ? data.get("seq").asInt() : null);
        }
    }

    public Room requireRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> ApiException.notFound("ROOM_NOT_FOUND", "방을 찾을 수 없습니다."));
    }

    private RoomDtos.RoomDto toDto(Room room) {
        return new RoomDtos.RoomDto(room, transcriptService.lastSeq(room.getId()));
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Agent 가 보내는 꼬리질문 후보 한 건. */
    @lombok.Getter
    @lombok.Setter
    public static class FollowUpInput {
        private String clientId;
        @javax.validation.constraints.NotBlank
        private String text;
        private String rationale;
    }
}
