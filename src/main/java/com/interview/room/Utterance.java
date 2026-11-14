package com.interview.room;

import javax.persistence.Column;
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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 확정 전사 한 줄. (roomId, seq)는 하나뿐이다. Agent 가 내부 API 로 저장한다. */
@Entity
@Table(name = "IV_UTTERANCE", uniqueConstraints = @UniqueConstraint(name = "UK_IV_UTTERANCE_SEQ", columnNames = {
        "ROOM_ID", "SEQ_NO" }))
@Getter
@Setter
@NoArgsConstructor
public class Utterance {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivUtteranceSeq")
    @SequenceGenerator(name = "ivUtteranceSeq", sequenceName = "IV_UTTERANCE_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ROOM_ID", nullable = false)
    private Long roomId;

    @Column(name = "SEQ_NO", nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "SPEAKER", nullable = false, length = 20)
    private Speaker speaker;

    @Enumerated(EnumType.STRING)
    @Column(name = "SPEAKER_METHOD", nullable = false, length = 20)
    private SpeakerMethod speakerMethod;

    @Lob
    @Column(name = "RAW_TEXT")
    private String rawText;

    @Lob
    @Column(name = "REFINED_TEXT")
    private String refinedText;

    @Column(name = "START_MS")
    private Long startMs;

    @Column(name = "END_MS")
    private Long endMs;

    @Column(name = "MATCHED_QUESTION_ID")
    private Long matchedQuestionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "SOURCE_KIND", nullable = false, length = 20)
    private TranscriptSource source;

    /** 백엔드가 이 줄을 저장한(마지막으로 덮어쓴) 시각. 운영 현황판의 "마지막 전사 시각"에 쓴다. */
    @Column(name = "SAVED_AT")
    private java.time.LocalDateTime savedAt;

    /** 평가·보고서에 쓰는 글: 다듬은 글이 있으면 그것, 없으면 원문. */
    public String bestText() {
        return refinedText != null && !refinedText.trim().isEmpty() ? refinedText : (rawText == null ? "" : rawText);
    }
}
