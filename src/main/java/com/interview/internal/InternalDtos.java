package com.interview.internal;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.agent.dto.AgentDtos;
import com.interview.interview.InterviewMode;
import com.interview.interview.InterviewType;
import com.interview.question.QuestionSource;
import com.interview.recruitment.Track;
import com.interview.room.RoomService;
import com.interview.room.RoomStatus;
import com.interview.room.UtteranceInput;

import lombok.Getter;
import lombok.Setter;

/** Agent → 백엔드 콜백(계약 5장)의 요청·응답. */
public final class InternalDtos {

    private InternalDtos() {
    }

    /** GET context 응답. 면접자의 이름·수험번호·블라인드 항목은 들어가지 않는다. */
    @Getter
    @Setter
    public static class RoomContext {
        private Long roomId;
        private Long interviewId;
        private RoomStatus status;
        private Track track;
        private InterviewType interviewType;
        private InterviewMode mode;
        private String jobTitle;
        private String jdText;
        private String candidateBrief;
        private List<ContextQuestion> questions = new ArrayList<ContextQuestion>();
        private List<AgentDtos.BankItem> questionBank = new ArrayList<AgentDtos.BankItem>();
    }

    @Getter
    public static class ContextQuestion {
        private final Long id;
        private final QuestionSource source;
        private final String category;
        private final String text;

        public ContextQuestion(Long id, QuestionSource source, String category, String text) {
            this.id = id;
            this.source = source;
            this.category = category;
            this.text = text;
        }
    }

    @Getter
    @Setter
    public static class UtterancesRequest {
        @NotNull
        @Valid
        private List<UtteranceInput> items;
    }

    @Getter
    @Setter
    public static class FollowUpsRequest {
        private Integer basedOnSeq;
        @NotEmpty
        @Valid
        private List<RoomService.FollowUpInput> items;
    }

    @Getter
    @Setter
    public static class RefinedTranscriptRequest {
        private String sttModel;
        @NotEmpty(message = "보정 전사가 비어 있습니다.")
        @Valid
        private List<UtteranceInput> items;
    }

    @Getter
    @Setter
    public static class EventRequest {
        @NotBlank
        private String type;
        private String message;
        private JsonNode data;
    }
}
