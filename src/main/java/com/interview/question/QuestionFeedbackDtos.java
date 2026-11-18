package com.interview.question;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** API 명세 2.6 의 QuestionFeedback 요청·응답. */
public final class QuestionFeedbackDtos {

    private QuestionFeedbackDtos() {
    }

    @Getter
    @Setter
    public static class VoteItem {
        @NotNull(message = "questionId 는 필수입니다.")
        private Long questionId;
        @NotNull(message = "vote 는 필수입니다.")
        private QuestionVote vote;

        public VoteItem() {
        }

        public VoteItem(Long questionId, QuestionVote vote) {
            this.questionId = questionId;
            this.vote = vote;
        }
    }

    /** PUT 본문. 내 의견을 통째로 바꾼다(overall 이 null 이면 고르지 않은 것, votes 가 없으면 빈 목록). */
    @Getter
    @Setter
    public static class SaveRequest {
        private FeedbackOverall overall;
        @Valid
        private List<VoteItem> votes;
    }

    /** 응답. 저장한 것이 없으면 overall 은 null, votes 는 빈 목록, updatedAt 은 null. */
    @Getter
    @Setter
    public static class Feedback {
        private Long interviewId;
        private FeedbackOverall overall;
        private List<VoteItem> votes = new ArrayList<VoteItem>();
        private OffsetDateTime updatedAt;
    }
}
