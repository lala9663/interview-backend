package com.interview.question;

import lombok.Getter;

/** 계약 2.6 의 Question. */
@Getter
public class QuestionDto {

    private final Long id;
    private final Long interviewId;
    private final QuestionSource source;
    private final String category;
    private final String text;
    private final String intent;
    private final int orderNo;
    private final boolean asked;
    private final Integer askedAtSeq;
    private final boolean hidden;
    private final Long parentQuestionId;

    private QuestionDto(Question q) {
        this.id = q.getId();
        this.interviewId = q.getInterviewId();
        this.source = q.getSource();
        this.category = q.getCategory();
        this.text = q.getText();
        this.intent = q.getIntent();
        this.orderNo = q.getOrderNo();
        this.asked = q.isAsked();
        this.askedAtSeq = q.getAskedAtSeq();
        this.hidden = q.isHidden();
        this.parentQuestionId = q.getParentQuestionId();
    }

    public static QuestionDto from(Question q) {
        return new QuestionDto(q);
    }
}
