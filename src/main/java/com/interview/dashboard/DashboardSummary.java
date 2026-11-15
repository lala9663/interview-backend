package com.interview.dashboard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.interview.interview.InterviewDto;

import lombok.Getter;
import lombok.Setter;

/**
 * GET /dashboard/summary 응답. API 명세에는 "수와 목록"이라고만 있어 이 모양으로 정했다(docs/open-questions.md).
 * 목록의 항목은 API 명세 2.5 의 Interview 와 같다. 목록은 각각 최대 100건.
 */
@Getter
@Setter
public class DashboardSummary {

    private LocalDate date;
    private long todayCount;
    private long liveRoomCount;
    private long pendingEvaluationCount;
    private long pendingReportCount;
    /** 오늘 면접. */
    private List<InterviewDto> todayInterviews = new ArrayList<InterviewDto>();
    /** 진행 중인 방(면접 상태 IN_PROGRESS). */
    private List<InterviewDto> liveRooms = new ArrayList<InterviewDto>();
    /** 평가 대기(면접 상태 EVALUATING. 면접관에게는 자신이 아직 제출하지 않은 것만). */
    private List<InterviewDto> pendingEvaluations = new ArrayList<InterviewDto>();
    /** 보고서 대기(방이 끝났는데 보고서가 아직 없는 면접). */
    private List<InterviewDto> pendingReports = new ArrayList<InterviewDto>();
}
