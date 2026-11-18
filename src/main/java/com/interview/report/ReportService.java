package com.interview.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.agent.dto.AgentDtos;
import com.interview.auth.CurrentUser;
import com.interview.briefing.BriefingService;
import com.interview.common.ApiException;
import com.interview.common.Times;
import com.interview.common.json.Json;
import com.interview.evaluation.AiEvaluationService;
import com.interview.interview.Interview;
import com.interview.interview.InterviewAccess;
import com.interview.interview.InterviewContext;
import com.interview.interview.InterviewRepository;
import com.interview.interview.InterviewStatus;
import com.interview.interview.LiveProperties;
import com.interview.job.AiJob;
import com.interview.job.AiJobService;
import com.interview.job.JobType;
import com.interview.room.TranscriptService;

import lombok.RequiredArgsConstructor;

/** 면접 후 보고서의 조회, Agent 요청 만들기, 새 버전 저장. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewAccess interviewAccess;
    private final InterviewContext.Loader contextLoader;
    private final TranscriptService transcriptService;
    private final BriefingService briefingService;
    private final AiEvaluationService aiEvaluationService;
    private final AiJobService jobService;
    private final LiveProperties liveProps;

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /** 최신 보고서. */
    @Transactional(readOnly = true)
    public ReportDto latest(Long interviewId) {
        interviewAccess.requireExisting(interviewId);
        Report report = reportRepository.findFirstByInterviewIdOrderByVersionDesc(interviewId).orElse(null);
        if (report == null) {
            return ReportDto.none(jobService.latestStatus(interviewId, JobType.REPORT));
        }
        return ReportDto.of(report);
    }

    /** 버전 목록(최신이 먼저). LIVE 초안 → REFINED 보정본 순으로 쌓인다. */
    @Transactional(readOnly = true)
    public List<ReportDto> versions(Long interviewId) {
        interviewAccess.requireExisting(interviewId);
        List<ReportDto> items = new ArrayList<ReportDto>();
        for (Report r : reportRepository.findByInterviewIdOrderByVersionDesc(interviewId)) {
            items.add(ReportDto.of(r));
        }
        return items;
    }

    /**
     * 같은 면접자의 바로 앞 차수(seq - 1, 취소된 면접 제외) 면접의 최신 보고서에 nextRoundChecks 가 있으면 돌려준다.
     * 앞 차수가 없거나, 보고서가 없거나, 항목이 없으면 null. 읽다가 예외가 나도 null(로그만 남긴다).
     * (API 명세 2.6 의 previousRoundChecks)
     */
    public PreviousRoundChecks previousRoundChecks(Long interviewId) {
        Interview interview = interviewAccess.requireExisting(interviewId);
        try {
            return readPreviousRoundChecks(interview);
        } catch (RuntimeException e) {
            // 앞 차수 보고서를 읽지 못해도(저장된 content 가 깨진 경우 등) 사전 리포트 조회는 되어야 한다.
            log.warn("앞 차수 보고서의 nextRoundChecks 를 읽지 못했습니다. interviewId={}", interviewId, e);
            return null;
        }
    }

    private PreviousRoundChecks readPreviousRoundChecks(Interview interview) {
        for (Interview prev : interviewRepository.findByCandidateIdOrderBySeqAsc(interview.getCandidateId())) {
            if (prev.getSeq() != interview.getSeq() - 1 || prev.getStatus() == InterviewStatus.CANCELED) {
                continue;
            }
            Report report = reportRepository.findFirstByInterviewIdOrderByVersionDesc(prev.getId()).orElse(null);
            if (report == null || report.getContent() == null) {
                continue;
            }
            List<String> items = new ArrayList<String>();
            for (JsonNode check : report.getContent().path("nextRoundChecks")) {
                if (check.isTextual() && !check.asText().trim().isEmpty()) {
                    items.add(check.asText());
                }
            }
            if (!items.isEmpty()) {
                return new PreviousRoundChecks(prev.getSeq(), prev.getType(), items);
            }
        }
        return null;
    }

    /**
     * 다시 만들기. 사후 STT 보정본이 있으면 REFINED, 없으면 LIVE 기준이다.
     * 실시간 기능을 쓰지 않는 면접(liveEnabled = false)은 보고서를 만들지 않으므로 409 LIVE_DISABLED.
     */
    @Transactional
    public AiJob regenerate(Long interviewId, CurrentUser user) {
        Interview interview = interviewAccess.requireWritable(interviewId, user);
        if (!liveProps.enabledFor(interview.getTrack())) {
            throw ApiException.conflict("LIVE_DISABLED", "실시간 기능을 쓰지 않는 면접이라 보고서를 만들지 않습니다.");
        }
        return submit(interviewId, transcriptService.hasRefined(interviewId) ? ReportBasis.REFINED : ReportBasis.LIVE);
    }

    @Transactional
    public AiJob submit(Long interviewId, ReportBasis basis) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("basis", basis.name());
        return jobService.submit(JobType.REPORT, interviewId, payload);
    }

    @Transactional(readOnly = true)
    public AgentDtos.ReportRequest buildRequest(Long interviewId, ReportBasis basis) {
        InterviewContext ctx = contextLoader.load(interviewId);
        Interview interview = ctx.getInterview();
        AgentDtos.ReportRequest request = new AgentDtos.ReportRequest();
        request.setTrack(interview.getTrack());
        request.setDetail(ReportDetail.of(interview.getTrack()));
        request.setBasis(basis);
        request.setInterviewType(interview.getType());
        request.setJobTitle(ctx.getJobTitle());
        request.setJdText(ctx.getJdText());
        request.setTranscript(transcriptService.linesOfInterview(interviewId));
        JsonNode summary = briefingService.summaryOf(interviewId);
        request.setBriefingSummary(summary == null ? Json.object() : summary);
        request.setAiEvaluation(aiEvaluationService.partForReport(interviewId));

        // 같은 면접자의 앞선 차수 가운데 보고서가 있는 것
        for (Interview prev : interviewRepository.findByCandidateIdOrderBySeqAsc(interview.getCandidateId())) {
            if (prev.getSeq() >= interview.getSeq()) {
                continue;
            }
            Report prevReport = reportRepository.findFirstByInterviewIdOrderByVersionDesc(prev.getId()).orElse(null);
            if (prevReport == null || prevReport.getContent() == null) {
                continue;
            }
            AgentDtos.PreviousRound round = new AgentDtos.PreviousRound();
            round.setType(prev.getType());
            round.setHeadline(prevReport.getContent().path("headline").asText(""));
            JsonNode checks = prevReport.getContent().path("nextRoundChecks");
            if (!checks.isArray() || checks.size() == 0) {
                checks = prevReport.getContent().path("concerns");
            }
            for (JsonNode check : checks) {
                round.getCheckPoints().add(check.asText());
            }
            request.getPreviousRounds().add(round);
        }
        return request;
    }

    /**
     * 새 버전으로 저장한다(기존 버전은 그대로 둔다).
     *
     * <p>초안(LIVE) 작업과 보정본(REFINED) 작업은 동시에 돌 수 있다(사후 STT 가 빨리 끝나면 겹친다).
     * 보정본이 이미 있으면 뒤늦게 끝난 초안은 저장하지 않는다: 최신 버전이 초안으로 되돌아가면 안 된다.
     * 두 작업이 같은 버전 번호를 잡아 유니크 제약에 걸리면 호출한 쪽({@link ReportJobHandler})이 다시 시도한다.
     */
    @Transactional
    public Report saveNewVersion(Long interviewId, ReportBasis basis, AgentDtos.ReportResponse response) {
        Interview interview = interviewAccess.requireExisting(interviewId);
        Report latest = reportRepository.findFirstByInterviewIdOrderByVersionDesc(interviewId).orElse(null);
        if (basis == ReportBasis.LIVE && latest != null && latest.getBasis() == ReportBasis.REFINED) {
            return latest;
        }
        Report report = new Report();
        report.setInterviewId(interviewId);
        report.setVersion(latest == null ? 1 : latest.getVersion() + 1);
        report.setBasis(basis);
        report.setDetail(ReportDetail.of(interview.getTrack()));
        report.setGeneratedAt(Times.now());
        report.setModel(response.getModel());
        report.setContent(response.getContent());
        return reportRepository.save(report);
    }
}
