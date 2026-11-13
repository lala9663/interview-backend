package com.interview.question;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

public final class QuestionRequests {

    private QuestionRequests() {
    }

    /** POST /interviews/{id}/questions */
    @Getter
    @Setter
    public static class Add {
        @NotBlank(message = "질문 내용은 필수입니다.")
        @Size(max = 1000)
        private String text;
        @Size(max = 100)
        private String category;
    }

    /** PATCH /questions/{id} — 보낸 필드만 바꾼다. */
    @Getter
    @Setter
    public static class Patch {
        @Size(max = 1000)
        private String text;
        private Integer orderNo;
        private Boolean asked;
        private Boolean hidden;
    }
}
