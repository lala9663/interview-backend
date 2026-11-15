package com.interview.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interview.auth.CurrentUser;
import com.interview.common.Times;
import com.interview.common.web.PageResponse;
import com.interview.evaluation.EvaluationService;
import com.interview.interview.InterviewDto;
import com.interview.interview.InterviewService;
import com.interview.interview.InterviewStatus;

import lombok.RequiredArgsConstructor;

/**
 * 대시보드 집계. ADMIN 은 전체, 면접관은 자신이 배정된 면접만 본다.
 * 면접 목록 조회(InterviewService.list)를 그대로 써서 조건을 한곳에서만 관리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final int LIMIT = 100;

    private final InterviewService interviewService;
    private final EvaluationService evaluationService;

    public DashboardSummary summary(Long recruitmentId, CurrentUser user) {
        boolean mine = !user.isAdmin();
        DashboardSummary summary = new DashboardSummary();
        summary.setDate(Times.today());

        PageResponse<InterviewDto> today = interviewService.list(mine, Times.today(), null, recruitmentId, 0, LIMIT, user);
        summary.setTodayInterviews(today.getItems());
        summary.setTodayCount(today.getTotal());

        PageResponse<InterviewDto> live = interviewService.list(mine, null, InterviewStatus.IN_PROGRESS, recruitmentId, 0,
                LIMIT, user);
        summary.setLiveRooms(live.getItems());
        summary.setLiveRoomCount(live.getTotal());

        PageResponse<InterviewDto> evaluating = interviewService.list(mine, null, InterviewStatus.EVALUATING,
                recruitmentId, 0, LIMIT, user);
        List<InterviewDto> pendingEvaluations = evaluating.getItems();
        long pendingEvaluationCount = evaluating.getTotal();
        if (mine) {
            List<Long> ids = new ArrayList<Long>();
            for (InterviewDto dto : pendingEvaluations) {
                ids.add(dto.getId());
            }
            Set<Long> submitted = evaluationService.submittedInterviewIds(user.getId(), ids);
            List<InterviewDto> notSubmitted = new ArrayList<InterviewDto>();
            for (InterviewDto dto : pendingEvaluations) {
                if (!submitted.contains(dto.getId())) {
                    notSubmitted.add(dto);
                }
            }
            pendingEvaluations = notSubmitted;
            pendingEvaluationCount = notSubmitted.size();
        }
        summary.setPendingEvaluations(pendingEvaluations);
        summary.setPendingEvaluationCount(pendingEvaluationCount);

        // 보고서 대기: 방이 끝난 면접(EVALUATING, DONE) 가운데 보고서가 아직 없는 것.
        // DONE 은 보통 보고서가 있으므로, 대기 중인 것은 대부분 EVALUATING 쪽에 있다.
        List<InterviewDto> pendingReports = new ArrayList<InterviewDto>();
        for (InterviewDto dto : evaluating.getItems()) {
            if (dto.getReportBasis() == null) {
                pendingReports.add(dto);
            }
        }
        PageResponse<InterviewDto> done = interviewService.list(mine, null, InterviewStatus.DONE, recruitmentId, 0, LIMIT,
                user);
        for (InterviewDto dto : done.getItems()) {
            if (dto.getReportBasis() == null) {
                pendingReports.add(dto);
            }
        }
        // 건수는 찾은 만큼, 목록은 API 명세대로 최대 LIMIT 건(EVALUATING 과 DONE 을 합치면 넘을 수 있다).
        summary.setPendingReportCount(pendingReports.size());
        if (pendingReports.size() > LIMIT) {
            pendingReports = new ArrayList<InterviewDto>(pendingReports.subList(0, LIMIT));
        }
        summary.setPendingReports(pendingReports);
        return summary;
    }
}
