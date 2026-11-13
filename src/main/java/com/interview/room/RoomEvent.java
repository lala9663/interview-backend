package com.interview.room;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.common.json.JsonNodeConverter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 방에서 일어난 일의 기록(Agent 가 보낸 POST_STT_FAILED, AUDIO_DROPPED, QUESTION_ASKED 와 백엔드가 남기는 FINALIZE_FAILED). */
@Entity
@Table(name = "IV_ROOM_EVENT", indexes = @Index(name = "IX_IV_ROOM_EVENT_ROOM", columnList = "ROOM_ID"))
@Getter
@Setter
@NoArgsConstructor
public class RoomEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivRoomEventSeq")
    @SequenceGenerator(name = "ivRoomEventSeq", sequenceName = "IV_ROOM_EVENT_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ROOM_ID", nullable = false)
    private Long roomId;

    @Column(name = "EVENT_TYPE", nullable = false, length = 50)
    private String type;

    @Column(name = "MESSAGE", length = 1000)
    private String message;

    @Lob
    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "DATA_JSON")
    private JsonNode data;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
