package com.interview.room;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 면접 방. 면접 한 건에 하나. 실시간 상태(접속 단말 등)는 Agent 가 Redis 에 둔다. */
@Entity
@Table(name = "IV_ROOM", uniqueConstraints = @UniqueConstraint(name = "UK_IV_ROOM_INTERVIEW", columnNames = "INTERVIEW_ID"))
@Getter
@Setter
@NoArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivRoomSeq")
    @SequenceGenerator(name = "ivRoomSeq", sequenceName = "IV_ROOM_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "INTERVIEW_ID", nullable = false)
    private Long interviewId;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private RoomStatus status;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    @Column(name = "ENDED_AT")
    private LocalDateTime endedAt;

    /** 사후 STT 보정본을 만든 모델. 보정본이 오기 전에는 null. */
    @Column(name = "POST_STT_MODEL", length = 100)
    private String postSttModel;
}
