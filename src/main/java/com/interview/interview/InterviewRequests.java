package com.interview.interview;

import java.time.OffsetDateTime;
import java.util.List;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 면접 관련 요청 본문 모음. */
public final class InterviewRequests {

    private InterviewRequests() {
    }

    /** POST /candidates/{id}/interviews */
    @Getter
    @Setter
    public static class Create {
        @NotNull(message = "면접 유형은 필수입니다.")
        private InterviewType type;
        private InterviewMode mode;
        private OffsetDateTime scheduledAt;
        @Min(1)
        @Max(600)
        private Integer durationMinutes;
    }

    /** PATCH /interviews/{id} — 보낸 필드만 바꾼다. */
    @Getter
    @Setter
    public static class Patch {
        private InterviewMode mode;
        private InterviewStatus status;
        private OffsetDateTime scheduledAt;
        @Min(1)
        @Max(600)
        private Integer durationMinutes;
    }

    /** PUT /interviews/{id}/persona */
    @Getter
    @Setter
    public static class SetPersona {
        @NotNull(message = "personaId 는 필수입니다.")
        private Long personaId;
    }

    /** PUT /interviews/{id}/interviewers */
    @Getter
    @Setter
    public static class SetInterviewers {
        @NotNull(message = "interviewerIds 는 필수입니다.")
        private List<Long> interviewerIds;
    }
}
