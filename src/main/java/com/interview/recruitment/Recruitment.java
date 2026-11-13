package com.interview.recruitment;

import java.time.LocalDate;
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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 채용(공고). */
@Entity
@Table(name = "IV_RECRUITMENT")
@Getter
@Setter
@NoArgsConstructor
public class Recruitment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivRecruitmentSeq")
    @SequenceGenerator(name = "ivRecruitmentSeq", sequenceName = "IV_RECRUITMENT_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRACK", nullable = false, length = 20)
    private Track track;

    @Column(name = "START_DT")
    private LocalDate startDate;

    @Column(name = "END_DT")
    private LocalDate endDate;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
