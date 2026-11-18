package com.interview.interview;

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

import com.interview.recruitment.Track;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 면접 한 건. 면접자 한 명이 여러 건(직무, 인성)을 가진다. */
@Entity
@Table(name = "IV_INTERVIEW", indexes = { @Index(name = "IX_IV_INTERVIEW_CAND", columnList = "CANDIDATE_ID"),
        @Index(name = "IX_IV_INTERVIEW_RECR", columnList = "RECRUITMENT_ID"),
        @Index(name = "IX_IV_INTERVIEW_SCHED", columnList = "SCHEDULED_AT") })
@Getter
@Setter
@NoArgsConstructor
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivInterviewSeq")
    @SequenceGenerator(name = "ivInterviewSeq", sequenceName = "IV_INTERVIEW_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CANDIDATE_ID", nullable = false)
    private Long candidateId;

    @Column(name = "RECRUITMENT_ID", nullable = false)
    private Long recruitmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRACK", nullable = false, length = 20)
    private Track track;

    /** TYPE 은 Oracle 예약어라 컬럼 이름은 INTERVIEW_TYPE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "INTERVIEW_TYPE", nullable = false, length = 20)
    private InterviewType type;

    /** 면접자 안에서의 차수(1부터). */
    @Column(name = "SEQ_NO", nullable = false)
    private int seq;

    /** MODE 는 Oracle 예약어라 컬럼 이름은 INTERVIEW_MODE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "INTERVIEW_MODE", nullable = false, length = 20)
    private InterviewMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private InterviewStatus status;

    @Column(name = "SCHEDULED_AT")
    private LocalDateTime scheduledAt;

    @Column(name = "DURATION_MINUTES")
    private Integer durationMinutes;

    /** 면접관이 고른 페르소나(persona.Persona 의 id). 고르지 않았으면 null 이고 기본 선택을 따른다. */
    @Column(name = "PERSONA_ID")
    private Long personaId;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
