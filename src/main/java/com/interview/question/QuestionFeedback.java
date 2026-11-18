package com.interview.question;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.common.json.JsonNodeConverter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 준비된 질문이 도움이 됐는지에 대한 면접관 한 명의 의견(API 명세 2.6). 면접 한 건에 사용자마다 하나. */
@Entity
@Table(name = "IV_QUESTION_FEEDBACK", uniqueConstraints = @UniqueConstraint(name = "UK_IV_Q_FEEDBACK_IV_USER", columnNames = {
        "INTERVIEW_ID", "USER_ID" }))
@Getter
@Setter
@NoArgsConstructor
public class QuestionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivQuestionFeedbackSeq")
    @SequenceGenerator(name = "ivQuestionFeedbackSeq", sequenceName = "IV_QUESTION_FEEDBACK_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "INTERVIEW_ID", nullable = false)
    private Long interviewId;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 전체 의견. 고르지 않았으면 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "OVERALL", length = 20)
    private FeedbackOverall overall;

    /** 질문별 의견: [ { "questionId": 7001, "vote": "UP" } ] */
    @Lob
    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "VOTES_JSON")
    private JsonNode votes;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;
}
