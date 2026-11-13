package com.interview.question;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 면접 한 건에 딸린 질문(준비된 질문 + 면접 중 추가된 질문). */
@Entity
@Table(name = "IV_QUESTION", indexes = @Index(name = "IX_IV_QUESTION_INTERVIEW", columnList = "INTERVIEW_ID"))
@Getter
@Setter
@NoArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivQuestionSeq")
    @SequenceGenerator(name = "ivQuestionSeq", sequenceName = "IV_QUESTION_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "INTERVIEW_ID", nullable = false)
    private Long interviewId;

    @Enumerated(EnumType.STRING)
    @Column(name = "SOURCE_KIND", nullable = false, length = 20)
    private QuestionSource source;

    @Column(name = "CATEGORY", length = 100)
    private String category;

    @Column(name = "QUESTION_TEXT", nullable = false, length = 1000)
    private String text;

    @Column(name = "INTENT", length = 1000)
    private String intent;

    @Column(name = "ORDER_NO", nullable = false)
    private int orderNo;

    @Column(name = "IS_ASKED", nullable = false)
    private boolean asked;

    /** 이 질문이 실제로 나온 발화의 seq. */
    @Column(name = "ASKED_AT_SEQ")
    private Integer askedAtSeq;

    @Column(name = "IS_HIDDEN", nullable = false)
    private boolean hidden;

    @Column(name = "PARENT_QUESTION_ID")
    private Long parentQuestionId;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
