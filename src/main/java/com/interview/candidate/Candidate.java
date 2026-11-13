package com.interview.candidate;

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

import com.interview.common.json.StringListConverter;
import com.interview.recruitment.Track;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 면접자. 같은 채용 안에서 수험번호(applicantNo)는 하나뿐이다. 블라인드 항목은 저장하지 않는다. */
@Entity
@Table(name = "IV_CANDIDATE", uniqueConstraints = @UniqueConstraint(name = "UK_IV_CANDIDATE_APPL_NO", columnNames = {
        "RECRUITMENT_ID", "APPLICANT_NO" }))
@Getter
@Setter
@NoArgsConstructor
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivCandidateSeq")
    @SequenceGenerator(name = "ivCandidateSeq", sequenceName = "IV_CANDIDATE_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "RECRUITMENT_ID", nullable = false)
    private Long recruitmentId;

    @Column(name = "APPLICANT_NO", nullable = false, length = 50)
    private String applicantNo;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRACK", nullable = false, length = 20)
    private Track track;

    @Column(name = "JOB_CODE", nullable = false, length = 30)
    private String jobCode;

    @Column(name = "JOB_TITLE", length = 200)
    private String jobTitle;

    @Lob
    @Convert(converter = CandidateProfileConverter.class)
    @Column(name = "PROFILE_JSON")
    private CandidateProfile profile = new CandidateProfile();

    /** 입력에 있었지만 버린 블라인드 항목의 키(gender, birthDate, family ...). 값은 남기지 않는다. */
    @Convert(converter = StringListConverter.class)
    @Column(name = "BLINDED_FIELDS", length = 500)
    private List<String> blindedFields = new ArrayList<String>();

    /** 들어온 경로: EXCEL, API, SEED. */
    @Column(name = "SOURCE_KIND", length = 20)
    private String sourceType;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
