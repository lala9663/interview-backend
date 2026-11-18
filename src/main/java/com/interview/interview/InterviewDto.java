package com.interview.interview;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.interview.candidate.ApplicantType;
import com.interview.job.JobStatus;
import com.interview.persona.PersonaDtos;
import com.interview.recruitment.Track;
import com.interview.report.ReportBasis;
import com.interview.room.RoomStatus;

import lombok.Getter;
import lombok.Setter;

/** API 명세 2.5 의 Interview. null 인 필드도 그대로 null 로 내보낸다. */
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
    /** 이 면접에서 실시간 기능(녹음, 전사, 꼬리질문 제안, 면접 뒤 보고서)을 쓰는가. */
    private boolean liveEnabled;
    /** 이 면접에 적용되는 페르소나의 요약. 쓰는 페르소나가 하나도 없으면 null. */
    private PersonaDtos.Summary persona;

    @Getter
    public static class CandidateRef {
        private final Long id;
        private final String name;
        private final String applicantNo;
        private final String jobTitle;
        private final ApplicantType applicantType;

        public CandidateRef(Long id, String name, String applicantNo, String jobTitle, ApplicantType applicantType) {
            this.id = id;
            this.name = name;
            this.applicantNo = applicantNo;
            this.jobTitle = jobTitle;
            this.applicantType = applicantType;
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
