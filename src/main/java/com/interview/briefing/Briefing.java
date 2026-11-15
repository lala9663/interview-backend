package com.interview.briefing;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
import com.interview.common.json.StringListConverter;
import com.interview.job.JobStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 사전 리포트(브리핑). 면접 한 건에 하나. 다시 만들면 덮어쓴다. */
@Entity
@Table(name = "IV_BRIEFING", uniqueConstraints = @UniqueConstraint(name = "UK_IV_BRIEFING_INTERVIEW", columnNames = "INTERVIEW_ID"))
@Getter
@Setter
@NoArgsConstructor
public class Briefing {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivBriefingSeq")
    @SequenceGenerator(name = "ivBriefingSeq", sequenceName = "IV_BRIEFING_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "INTERVIEW_ID", nullable = false)
    private Long interviewId;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "GENERATED_AT")
    private LocalDateTime generatedAt;

    @Column(name = "MODEL_NAME", length = 100)
    private String model;

    /** API 명세 2.6 의 summary (headline, keyPoints, strengths, checkPoints, timeline). */
    @Lob
    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "SUMMARY_JSON")
    private JsonNode summary;

    /** 요약에서 빠진 블라인드 항목의 키. */
    @Convert(converter = StringListConverter.class)
    @Column(name = "EXCLUDED_FIELDS", length = 500)
    private List<String> excluded = new ArrayList<String>();
}
