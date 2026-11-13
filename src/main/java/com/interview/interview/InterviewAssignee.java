package com.interview.interview;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 면접에 배정된 면접관. */
@Entity
@Table(name = "IV_INTERVIEW_ASSIGNEE", uniqueConstraints = @UniqueConstraint(name = "UK_IV_ASSIGNEE", columnNames = {
        "INTERVIEW_ID", "USER_ID" }), indexes = @Index(name = "IX_IV_ASSIGNEE_USER", columnList = "USER_ID"))
@Getter
@Setter
@NoArgsConstructor
public class InterviewAssignee {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivAssigneeSeq")
    @SequenceGenerator(name = "ivAssigneeSeq", sequenceName = "IV_INTERVIEW_ASSIGNEE_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "INTERVIEW_ID", nullable = false)
    private Long interviewId;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "MATCHED_BY", nullable = false, length = 20)
    private MatchedBy matchedBy;

    public InterviewAssignee(Long interviewId, Long userId, MatchedBy matchedBy) {
        this.interviewId = interviewId;
        this.userId = userId;
        this.matchedBy = matchedBy;
    }
}
