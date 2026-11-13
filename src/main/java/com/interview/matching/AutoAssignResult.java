package com.interview.matching;

import java.util.ArrayList;
import java.util.List;

import com.interview.interview.InterviewType;

import lombok.Getter;
import lombok.Setter;

/** POST /recruitments/{id}/assignments/auto 의 결과 요약. */
@Getter
@Setter
public class AutoAssignResult {

    /** 채용의 면접자 수. */
    private int candidateCount;
    /** 이번에 새로 만든 면접 수. */
    private int createdInterviewCount;
    /** 이번에 면접관을 배정한 면접 수. */
    private int assignedInterviewCount;
    /** 맞는 면접관이 없어 배정하지 못한 면접 수. */
    private int unassignedInterviewCount;
    /** 이번에 접수한 브리핑 작업 수(app.features.auto-briefing-on-match). */
    private int briefingJobCount;
    private List<Unassigned> unassigned = new ArrayList<Unassigned>();

    @Getter
    public static class Unassigned {
        private final Long interviewId;
        private final Long candidateId;
        private final String candidateName;
        private final String jobCode;
        private final InterviewType type;

        public Unassigned(Long interviewId, Long candidateId, String candidateName, String jobCode, InterviewType type) {
            this.interviewId = interviewId;
            this.candidateId = candidateId;
            this.candidateName = candidateName;
            this.jobCode = jobCode;
            this.type = type;
        }
    }
}
