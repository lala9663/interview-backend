package com.interview.candidate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.interview.interview.InterviewDto;
import com.interview.interview.InterviewMode;
import com.interview.interview.InterviewStatus;
import com.interview.interview.InterviewType;
import com.interview.job.JobStatus;
import com.interview.recruitment.Track;

import lombok.Getter;
import lombok.Setter;

/** 계약 2.3 의 면접자 응답 모음. */
public final class CandidateDtos {

    private CandidateDtos() {
    }

    @Getter
    public static class Stage {
        private final InterviewType type;
        private final InterviewStatus status;

        public Stage(InterviewType type, InterviewStatus status) {
            this.type = type;
            this.status = status;
        }
    }

    @Getter
    public static class PersonRef {
        private final Long id;
        private final String name;

        public PersonRef(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** 목록 항목. */
    @Getter
    @Setter
    public static class ListItem {
        private Long id;
        private String applicantNo;
        private String name;
        private Track track;
        private String jobCode;
        private String jobTitle;
        private List<PersonRef> interviewers = new ArrayList<PersonRef>();
        private JobStatus briefingStatus;
        private Stage currentStage;
    }

    /** 상세. profile 은 블라인드 항목이 이미 빠진 값이다. */
    @Getter
    @Setter
    public static class Detail {
        private Long id;
        private String applicantNo;
        private String name;
        private Track track;
        private String jobCode;
        private String jobTitle;
        private CandidateProfile profile;
        private List<String> blindedFields;
        private List<InterviewDto> interviews;
    }

    /** GET /candidates/{id}/progress 의 한 차수. */
    @Getter
    @Setter
    public static class ProgressItem {
        private Long interviewId;
        private int seq;
        private InterviewType type;
        private InterviewMode mode;
        private InterviewStatus status;
        private OffsetDateTime scheduledAt;
        private JobStatus reportStatus;
        /** 지금 차수이면 true. */
        private boolean current;
    }

    @Getter
    @Setter
    public static class Progress {
        private Long candidateId;
        private Stage currentStage;
        private List<ProgressItem> items = new ArrayList<ProgressItem>();
    }
}
