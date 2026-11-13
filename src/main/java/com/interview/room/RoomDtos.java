package com.interview.room;

import java.time.OffsetDateTime;

import com.interview.common.Times;

import lombok.Getter;

/** 계약 2.7 의 방 관련 응답. */
public final class RoomDtos {

    private RoomDtos() {
    }

    @Getter
    public static class RoomDto {
        private final Long id;
        private final Long interviewId;
        private final RoomStatus status;
        private final OffsetDateTime createdAt;
        private final OffsetDateTime startedAt;
        private final OffsetDateTime endedAt;
        /** 저장된 전사의 마지막 seq (없으면 0). */
        private final int lastSeq;
        /** 사후 STT 보정본이 반영됐는지. */
        private final boolean refined;

        public RoomDto(Room room, int lastSeq) {
            this.id = room.getId();
            this.interviewId = room.getInterviewId();
            this.status = room.getStatus();
            this.createdAt = Times.toOffset(room.getCreatedAt());
            this.startedAt = Times.toOffset(room.getStartedAt());
            this.endedAt = Times.toOffset(room.getEndedAt());
            this.lastSeq = lastSeq;
            this.refined = room.getPostSttModel() != null;
        }
    }

    @Getter
    public static class Ticket {
        private final String ticket;
        private final String agentWsUrl;
        private final long expiresIn;

        public Ticket(String ticket, String agentWsUrl, long expiresIn) {
            this.ticket = ticket;
            this.agentWsUrl = agentWsUrl;
            this.expiresIn = expiresIn;
        }
    }

    @Getter
    public static class UtteranceDto {
        private final int seq;
        private final Speaker speaker;
        private final SpeakerMethod speakerMethod;
        private final String rawText;
        private final String refinedText;
        private final Long startMs;
        private final Long endMs;
        private final Long matchedQuestionId;
        private final TranscriptSource source;

        public UtteranceDto(Utterance u) {
            this.seq = u.getSeq();
            this.speaker = u.getSpeaker();
            this.speakerMethod = u.getSpeakerMethod();
            this.rawText = u.getRawText();
            this.refinedText = u.getRefinedText();
            this.startMs = u.getStartMs();
            this.endMs = u.getEndMs();
            this.matchedQuestionId = u.getMatchedQuestionId();
            this.source = u.getSource();
        }
    }

    @Getter
    public static class FollowUpDto {
        private final Long id;
        private final String clientId;
        private final Integer basedOnSeq;
        private final String text;
        private final String rationale;
        private final boolean used;
        private final Long questionId;
        private final OffsetDateTime createdAt;

        public FollowUpDto(FollowUp f) {
            this.id = f.getId();
            this.clientId = f.getClientId();
            this.basedOnSeq = f.getBasedOnSeq();
            this.text = f.getText();
            this.rationale = f.getRationale();
            this.used = f.isUsed();
            this.questionId = f.getQuestionId();
            this.createdAt = Times.toOffset(f.getCreatedAt());
        }
    }
}
