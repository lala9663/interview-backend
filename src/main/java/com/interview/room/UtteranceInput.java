package com.interview.room;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** Agent 가 보내는 전사 한 줄(API 명세 5장의 utterances / refined-transcript 의 items[]). */
@Getter
@Setter
public class UtteranceInput {

    @NotNull
    @Min(1)
    private Integer seq;
    private Speaker speaker;
    private SpeakerMethod speakerMethod;
    private String rawText;
    private String refinedText;
    private Long startMs;
    private Long endMs;
    private Long matchedQuestionId;
}
