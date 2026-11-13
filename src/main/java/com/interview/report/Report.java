package com.interview.report;

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

/** 면접 후 보고서의 한 버전. 만들 때마다 버전이 하나씩 늘어난다(성공한 것만 저장). */
@Entity
@Table(name = "IV_REPORT", uniqueConstraints = @UniqueConstraint(name = "UK_IV_REPORT_VERSION", columnNames = {
        "INTERVIEW_ID", "VERSION_NO" }))
@Getter
@Setter
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivReportSeq")
    @SequenceGenerator(name = "ivReportSeq", sequenceName = "IV_REPORT_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "INTERVIEW_ID", nullable = false)
    private Long interviewId;

    @Column(name = "VERSION_NO", nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "BASIS", nullable = false, length = 20)
    private ReportBasis basis;

    @Enumerated(EnumType.STRING)
    @Column(name = "DETAIL_LEVEL", nullable = false, length = 20)
    private ReportDetail detail;

    @Column(name = "GENERATED_AT", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "MODEL_NAME", length = 100)
    private String model;

    /** 계약 4.3 의 ReportContent. */
    @Lob
    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "CONTENT_JSON")
    private JsonNode content;
}
