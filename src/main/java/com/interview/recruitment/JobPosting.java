package com.interview.recruitment;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
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

/** 채용에 딸린 직무와 JD. (API 명세의 /recruitments/{id}/jobs, PUT /jobs/{id}) */
@Entity
@Table(name = "IV_JOB_POSTING", uniqueConstraints = @UniqueConstraint(name = "UK_IV_JOB_POSTING_CODE", columnNames = {
        "RECRUITMENT_ID", "JOB_CODE" }))
@Getter
@Setter
@NoArgsConstructor
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivJobPostingSeq")
    @SequenceGenerator(name = "ivJobPostingSeq", sequenceName = "IV_JOB_POSTING_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "RECRUITMENT_ID", nullable = false)
    private Long recruitmentId;

    @Column(name = "JOB_CODE", nullable = false, length = 30)
    private String jobCode;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "JD_TEXT")
    private String jdText;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
