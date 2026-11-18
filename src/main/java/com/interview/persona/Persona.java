package com.interview.persona;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 면접 페르소나(API 명세 2.12). 면접관이 면접 전에 고르는 진행 방식이다.
 * 만든 순서는 ID(시퀀스) 순서다. 기본 선택을 정할 때 이 순서를 쓴다.
 */
@Entity
@Table(name = "IV_PERSONA", uniqueConstraints = @UniqueConstraint(name = "UK_IV_PERSONA_NAME", columnNames = "NAME"))
@Getter
@Setter
@NoArgsConstructor
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivPersonaSeq")
    @SequenceGenerator(name = "ivPersonaSeq", sequenceName = "IV_PERSONA_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    /** 질문의 종류를 가리키는 짧은 말(예: 행동 사례 질문). TYPE 은 Oracle 예약어라 컬럼 이름은 QUESTION_KIND. */
    @Column(name = "QUESTION_KIND", length = 100)
    private String questionType;

    /** 꼬리질문의 종류를 가리키는 짧은 말(예: 빠진 단계 보완). */
    @Column(name = "FOLLOW_UP_KIND", length = 100)
    private String followUpType;

    /** 이 페르소나가 맞는 상황. */
    @Column(name = "FIT_NOTE", length = 500)
    private String fit;

    /** 이 페르소나에서 지킬 것(질문을 만들 때의 제한). */
    @Column(name = "GUARD_NOTE", length = 1000)
    private String guard;

    /** 화면의 표시 색 이름: brand | ok | warn | bad | neutral. */
    @Column(name = "TONE", length = 20)
    private String tone;

    @Column(name = "IS_RECOMMENDED", nullable = false)
    private boolean recommended;

    @Column(name = "IS_ENABLED", nullable = false)
    private boolean enabled;

    /** 관리자가 직접 만든 것이면 true. 기본 페르소나(false)는 지울 수 없다. */
    @Column(name = "IS_CUSTOM", nullable = false)
    private boolean custom;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
