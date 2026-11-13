package com.interview.room;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Agent 가 제안한 꼬리질문 후보. 면접관이 채택하면 질문(IV_QUESTION)이 된다. */
@Entity
@Table(name = "IV_FOLLOW_UP", indexes = @Index(name = "IX_IV_FOLLOW_UP_ROOM", columnList = "ROOM_ID"))
@Getter
@Setter
@NoArgsConstructor
public class FollowUp {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivFollowUpSeq")
    @SequenceGenerator(name = "ivFollowUpSeq", sequenceName = "IV_FOLLOW_UP_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ROOM_ID", nullable = false)
    private Long roomId;

    @Column(name = "BASED_ON_SEQ")
    private Integer basedOnSeq;

    /** Agent 가 붙인 id (WebSocket 의 followup.suggested.items[].id 와 같다). */
    @Column(name = "CLIENT_ID", length = 100)
    private String clientId;

    @Column(name = "FOLLOW_UP_TEXT", nullable = false, length = 1000)
    private String text;

    @Column(name = "RATIONALE", length = 1000)
    private String rationale;

    @Column(name = "IS_USED", nullable = false)
    private boolean used;

    /** 채택돼 만들어진 질문의 id. */
    @Column(name = "QUESTION_ID")
    private Long questionId;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
