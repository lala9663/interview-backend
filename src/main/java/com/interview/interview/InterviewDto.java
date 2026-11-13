package com.interview.interview;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.interview.job.JobStatus;
import com.interview.recruitment.Track;
import com.interview.report.ReportBasis;
import com.interview.room.RoomStatus;

import lombok.Getter;
import lombok.Setter;

/** 계약 2.5 의 Interview. null 인 필드도 그대로 null 로 내보낸다. */
@Getter
@Setter
public class InterviewDto {

    private Long id;
    private CandidateRef candidate;
    private Track track;
    private InterviewType type;
    private int seq;
    private InterviewMode mode;
    private InterviewStatus status;
    private OffsetDateTime scheduledAt;
    private Integer durationMinutes;
    private List<InterviewerRef> interviewers = new ArrayList<InterviewerRef>();
    private Long roomId;
    private RoomStatus roomStatus;
    private JobStatus briefingStatus;
    private JobStatus reportStatus;
    private ReportBasis reportBasis;

    @Getter
    public static class CandidateRef {
        private final Long id;
        private final String name;
        private final String applicantNo;
        private final String jobTitle;

        public CandidateRef(Long id, String name, String applicantNo, String jobTitle) {
            this.id = id;
            this.name = name;
            this.applicantNo = applicantNo;
            this.jobTitle = jobTitle;
        }
    }

    @Getter
    public static class InterviewerRef {
        private final Long id;
        private final String name;
        private final MatchedBy matchedBy;

        public InterviewerRef(Long id, String name, MatchedBy matchedBy) {
            this.id = id;
            this.name = name;
            this.matchedBy = matchedBy;
        }
    }
}
