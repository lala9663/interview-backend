package com.interview.briefing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.agent.dto.AgentDtos;
import com.interview.auth.CurrentUser;
import com.interview.candidate.CandidateProfile;
import com.interview.common.Times;
import com.interview.interview.InterviewAccess;
import com.interview.interview.InterviewContext;
import com.interview.job.AiJob;
import com.interview.job.AiJobService;
import com.interview.job.JobStatus;
import com.interview.job.JobType;
import com.interview.question.Question;
import com.interview.question.QuestionService;
import com.interview.question.QuestionSource;
import com.interview.questionbank.QuestionBankItem;
import com.interview.questionbank.QuestionBankService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BriefingService {

    private final BriefingRepository briefingRepository;
    private final InterviewAccess interviewAccess;
    private final InterviewContext.Loader contextLoader;
    private final QuestionBankService questionBankService;
    private final QuestionService questionService;
    private final AiJobService jobService;
    private final BriefingProperties props;

    @Transactional(readOnly = true)
    public BriefingDto get(Long interviewId) {
        interviewAccess.requireExisting(interviewId);
        return BriefingDto.from(briefingRepository.findByInterviewId(interviewId).orElse(null));
    }

    /** 요약 JSON. 없으면 null. (AI 평가·보고서 요청에 넣는다.) */
    @Transactional(readOnly = true)
    public JsonNode summaryOf(Long interviewId) {
        Briefing b = briefingRepository.findByInterviewId(interviewId).orElse(null);
        return b == null ? null : b.getSummary();
    }

    /** 방 context 의 candidateBrief: headline + keyPoints. 블라인드 처리된 요약에서만 만든다. */
    @Transactional(readOnly = true)
    public String candidateBrief(Long interviewId) {
        JsonNode summary = summaryOf(interviewId);
        if (summary == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(summary.path("headline").asText(""));
        for (JsonNode point : summary.path("keyPoints")) {
            sb.append("\n- ").append(point.asText());
        }
        return sb.toString();
    }

    @Transactional
    public AiJob requestGenerate(Long interviewId, CurrentUser user) {
        interviewAccess.requireWritable(interviewId, user);
        return requestGenerate(interviewId);
    }

    /** 권한 검사 없이 접수한다(자동 매칭 뒤의 일괄 생성). */
    @Transactional
    public AiJob requestGenerate(Long interviewId) {
        Briefing b = briefingRepository.findByInterviewId(interviewId).orElse(null);
        if (b == null) {
            b = new Briefing();
            b.setInterviewId(interviewId);
        }
        b.setStatus(JobStatus.PENDING);
        briefingRepository.save(b);
        return jobService.submit(JobType.BRIEFING, interviewId, null);
    }

    @Transactional
    public void markStatus(Long interviewId, JobStatus status) {
        Briefing b = briefingRepository.findByInterviewId(interviewId).orElse(null);
        if (b != null) {
            b.setStatus(status);
        }
    }

    /** Agent 요청을 만든다. 면접자의 이름·수험번호는 넣지 않는다. profile 은 블라인드 항목이 빠진 값이다. */
    @Transactional(readOnly = true)
    public AgentDtos.BriefingRequest buildRequest(Long interviewId) {
        InterviewContext ctx = contextLoader.load(interviewId);
        AgentDtos.BriefingRequest request = new AgentDtos.BriefingRequest();
        request.setTrack(ctx.getInterview().getTrack());
        request.setInterviewType(ctx.getInterview().getType());
        request.setJobTitle(ctx.getJobTitle());
        request.setJdText(ctx.getJdText());
        CandidateProfile profile = ctx.getCandidate().getProfile();
        request.setCandidate(new AgentDtos.CandidatePart(profile == null ? new CandidateProfile() : profile));
        for (QuestionBankItem item : questionBankService.usableFor(ctx.getCandidate().getJobCode(),
                ctx.getInterview().getType(), props.getQuestionBankLimit())) {
            request.getQuestionBank().add(new AgentDtos.BankItem(item.getId(), item.getCategory(), item.getText()));
        }
        request.setQuestionCount(new AgentDtos.QuestionCount(props.getJdQuestionCount(), props.getResumeQuestionCount()));
        return request;
    }

    @Transactional
    public void saveResult(Long interviewId, AgentDtos.BriefingResponse response) {
        InterviewContext ctx = contextLoader.load(interviewId);
        Briefing b = briefingRepository.findByInterviewId(interviewId).orElse(null);
        if (b == null) {
            b = new Briefing();
            b.setInterviewId(interviewId);
        }
        b.setStatus(JobStatus.SUCCEEDED);
        b.setGeneratedAt(Times.now());
        b.setModel(response.getModel());
        b.setSummary(response.getSummary());
        // 빠진 항목 = 업로드 때 버린 블라인드 항목 + Agent 가 글에서 걸러 낸 항목
        Set<String> excluded = new LinkedHashSet<String>(ctx.getCandidate().getBlindedFields());
        if (response.getExcluded() != null) {
            excluded.addAll(response.getExcluded());
        }
        b.setExcluded(new ArrayList<String>(excluded));
        briefingRepository.save(b);

        List<Question> generated = new ArrayList<Question>();
        if (response.getQuestions() != null) {
            for (AgentDtos.GeneratedQuestion g : response.getQuestions()) {
                if (g.getText() == null || g.getText().trim().isEmpty()) {
                    continue;
                }
                Question q = new Question();
                q.setSource(parseSource(g.getSource()));
                q.setCategory(cut(g.getCategory(), 100));
                q.setText(cut(g.getText().trim(), 1000));
                q.setIntent(cut(g.getIntent(), 1000));
                generated.add(q);
            }
        }
        questionService.replaceGenerated(interviewId, generated);
    }

    private static QuestionSource parseSource(String source) {
        if ("JD".equalsIgnoreCase(source)) {
            return QuestionSource.JD;
        }
        if ("BANK".equalsIgnoreCase(source)) {
            return QuestionSource.BANK;
        }
        return QuestionSource.RESUME;
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
