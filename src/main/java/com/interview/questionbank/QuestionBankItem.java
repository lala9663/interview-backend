package com.interview.questionbank;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.interview.interview.InterviewType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 질문 은행. jobCode 나 interviewType 이 null 이면 "공통"이다. */
@Entity
@Table(name = "IV_QUESTION_BANK", indexes = @Index(name = "IX_IV_QBANK_JOB", columnList = "JOB_CODE"))
@Getter
@Setter
@NoArgsConstructor
public class QuestionBankItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivQuestionBankSeq")
    @SequenceGenerator(name = "ivQuestionBankSeq", sequenceName = "IV_QUESTION_BANK_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "JOB_CODE", length = 30)
    private String jobCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "INTERVIEW_TYPE", length = 20)
    private InterviewType interviewType;

    @Column(name = "CATEGORY", length = 100)
    private String category;

    @Column(name = "QUESTION_TEXT", nullable = false, length = 1000)
    private String text;

    @Lob
    @Column(name = "SAMPLE_ANSWER")
    private String sampleAnswer;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
