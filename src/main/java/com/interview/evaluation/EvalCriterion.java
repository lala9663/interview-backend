package com.interview.evaluation;

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

import com.interview.interview.InterviewType;
import com.interview.recruitment.Track;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 평가 항목. 트랙·면접 유형별로 정한다(기준 데이터). */
@Entity
@Table(name = "IV_EVAL_CRITERION", uniqueConstraints = @UniqueConstraint(name = "UK_IV_EVAL_CRITERION", columnNames = {
        "TRACK", "INTERVIEW_TYPE", "CODE" }))
@Getter
@Setter
@NoArgsConstructor
public class EvalCriterion {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivEvalCriterionSeq")
    @SequenceGenerator(name = "ivEvalCriterionSeq", sequenceName = "IV_EVAL_CRITERION_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRACK", nullable = false, length = 20)
    private Track track;

    @Enumerated(EnumType.STRING)
    @Column(name = "INTERVIEW_TYPE", nullable = false, length = 20)
    private InterviewType interviewType;

    @Column(name = "CODE", nullable = false, length = 50)
    private String code;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Column(name = "MAX_SCORE", nullable = false)
    private int maxScore;

    @Column(name = "ORDER_NO", nullable = false)
    private int orderNo;
}
