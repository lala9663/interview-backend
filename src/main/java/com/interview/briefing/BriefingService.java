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
import com.interview.persona.Persona;
import com.interview.question.Question;
import com.interview.question.QuestionService;
import com.interview.question.QuestionSource;
import com.interview.questionbank.QuestionBankItem;
import com.interview.questionbank.QuestionBankService;
import com.interview.report.PreviousRoundChecks;

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

    /** 실시간 기능을 쓰지 않는 면접에서 질문마다 요청하는 예상 꼬리질문의 수(API 명세 4.1). */
    private static final int PREDICTED_FOLLOW_UPS = 3;

    /** 작업(AiJob)의 payload 에 무엇을 만드는지 적어 두는 이름. */
    static final String TARGET = "target";

    /** 사전 리포트. previousRoundChecks 는 호출한 쪽(컨트롤러)이 report.ReportService 에서 읽어 넘긴다. */
    @Transactional(readOnly = true)
    public BriefingDto get(Long interviewId, PreviousRoundChecks previousRoundChecks) {
        interviewAccess.requireExisting(interviewId);
        Briefing b = briefingRepository.findByInterviewId(interviewId).orElse(null);
        BriefingTarget generating = null;
        if (b != null && (b.getStatus() == JobStatus.PENDING || b.getStatus() == JobStatus.RUNNING)) {
            generating = BriefingTarget.ofJob(jobService.latestPayloadText(interviewId, JobType.BRIEFING, TARGET));
        }
        return BriefingDto.from(b, previousRoundChecks, generating);
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
    public AiJob requestGenerate(Long interviewId, CurrentUser user, BriefingTarget target) {
        interviewAccess.requireWritable(interviewId, user);
        return requestGenerate(interviewId, target);
    }

    /** 권한 검사 없이 요약과 질문을 모두 만드는 작업을 접수한다(자동 매칭 뒤의 일괄 생성). */
    @Transactional
    public AiJob requestGenerate(Long interviewId) {
        return requestGenerate(interviewId, BriefingTarget.ALL);
    }

    /** 권한 검사 없이 접수한다. target 이 SUMMARY 면 요약만, QUESTIONS 면 질문만 바꾼다. */
    @Transactional
    public AiJob requestGenerate(Long interviewId, BriefingTarget target) {
        Briefing b = briefingRepository.findByInterviewId(interviewId).orElse(null);
        if (b == null) {
            b = new Briefing();
            b.setInterviewId(interviewId);
        }
        b.setStatus(JobStatus.PENDING);
        briefingRepository.save(b);
        return jobService.submit(JobType.BRIEFING, interviewId,
                target == BriefingTarget.ALL ? null : java.util.Collections.<String, Object>singletonMap(TARGET, target.name()));
    }

    /**
     * 사전 리포트가 이미 만들어져 있으면 질문을 다시 만드는 작업을 접수한다(면접의 페르소나를 바꿨을 때. 요약은 그대로 둔다).
     * 아직 만든 적이 없거나 이미 만드는 작업이 돌고 있으면(PENDING·RUNNING) 아무것도 하지 않고 null 을 돌려준다.
     * 돌고 있던 작업이 바꾸기 전의 페르소나로 끝나면 {@link #regenerateIfPersonaChanged} 가 한 번 더 만든다.
     */
    @Transactional
    public AiJob regenerateIfExists(Long interviewId) {
        Briefing b = briefingRepository.findByInterviewId(interviewId).orElse(null);
        if (b == null || b.getGeneratedAt() == null) {
            return null;
        }
        JobStatus running = jobService.latestStatus(interviewId, JobType.BRIEFING);
        if (running == JobStatus.PENDING || running == JobStatus.RUNNING) {
            return null;
        }
        return requestGenerate(interviewId, BriefingTarget.QUESTIONS);
    }

    /**
     * 브리핑 작업이 성공으로 끝난 뒤: 그 작업에 적용한 페르소나와 지금 적용되는 페르소나가 다르면 한 번 더 만든다.
     * (작업이 도는 동안 페르소나를 바꾸면 새 작업을 접수하지 않으므로, 끝난 뒤에 여기서 따라잡는다.)
     * 새 작업은 지금의 페르소나로 만들어지므로, 그 사이에 또 바꾸지 않는 한 다시 접수되지 않는다.
     * 이미 다른 브리핑 작업이 기다리거나 돌고 있으면 그 작업에 맡긴다.
     */
    @Transactional
    public AiJob regenerateIfPersonaChanged(Long interviewId) {
        Briefing b = briefingRepository.findByInterviewId(interviewId).orElse(null);
        if (b == null || b.getGeneratedAt() == null) {
            return null;
        }
        Persona current = contextLoader.load(interviewId).getPersona();
        Long currentId = current == null ? null : current.getId();
        if (currentId == null ? b.getPersonaId() == null : currentId.equals(b.getPersonaId())) {
            return null;
        }
        JobStatus latest = jobService.latestStatus(interviewId, JobType.BRIEFING);
        if (latest == JobStatus.PENDING || latest == JobStatus.RUNNING) {
            return null;
        }
        return requestGenerate(interviewId, BriefingTarget.QUESTIONS);
    }

    @Transactional
    public void markStatus(Long interviewId, JobStatus status) {
        Briefing b = briefingRepository.findByInterviewId(interviewId).orElse(null);
        if (b != null) {
            b.setStatus(status);
        }
    }

    /** Agent 요청과, 그 요청에 적용한 페르소나의 id(없으면 null). */
    public static class Prepared {
        private final AgentDtos.BriefingRequest request;
        private final Long personaId;

        Prepared(AgentDtos.BriefingRequest request, Long personaId) {
            this.request = request;
            this.personaId = personaId;
        }

        public AgentDtos.BriefingRequest getRequest() {
            return request;
        }

        public Long getPersonaId() {
            return personaId;
        }
    }

    /** 작업을 시작할 때: Agent 요청을 만들고, 그때 적용한 페르소나를 함께 돌려준다(결과를 저장할 때 기록한다). */
    @Transactional(readOnly = true)
    public Prepared prepare(Long interviewId, BriefingTarget target) {
        InterviewContext ctx = contextLoader.load(interviewId);
        AgentDtos.BriefingRequest request = buildRequest(ctx);
        if (target != BriefingTarget.ALL) {
            request.setParts(java.util.Collections.singletonList(target.name()));
        }
        return new Prepared(request, ctx.getPersona() == null ? null : ctx.getPersona().getId());
    }

    /** Agent 요청을 만든다. 면접자의 이름·수험번호는 넣지 않는다. profile 은 블라인드 항목이 빠진 값이다. */
    @Transactional(readOnly = true)
    public AgentDtos.BriefingRequest buildRequest(Long interviewId) {
        return buildRequest(contextLoader.load(interviewId));
    }

    private AgentDtos.BriefingRequest buildRequest(InterviewContext ctx) {
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
        request.setApplicantType(ctx.getCandidate().getApplicantType());
        request.setPersona(personaPart(ctx.getPersona()));
        // 실시간 기능을 쓰지 않는 면접은 면접 중에 꼬리질문 제안이 없으므로, 질문마다 예상 꼬리질문을 미리 만들어 달라고 한다.
        if (!ctx.isLiveEnabled()) {
            request.setPredictedFollowUps(PREDICTED_FOLLOW_UPS);
        }
        return request;
    }

    /** Agent 로 보내는 페르소나 부분(이름, 설명, 질문·꼬리질문 유형, 지킬 것). 페르소나가 없으면 null. */
    public static AgentDtos.PersonaPart personaPart(Persona persona) {
        if (persona == null) {
            return null;
        }
        return new AgentDtos.PersonaPart(persona.getName(), persona.getDescription(), persona.getQuestionType(),
                persona.getFollowUpType(), persona.getGuard());
    }

    /**
     * 결과를 저장한다. personaId 는 요청을 만들 때 적용한 페르소나({@link Prepared#getPersonaId()})다.
     * target 이 SUMMARY 면 요약만, QUESTIONS 면 질문만 바꾼다(Agent 가 둘 다 돌려줘도 나머지는 버린다).
     */
    @Transactional
    public void saveResult(Long interviewId, AgentDtos.BriefingResponse response, Long personaId, BriefingTarget target) {
        InterviewContext ctx = contextLoader.load(interviewId);
        Briefing b = briefingRepository.findByInterviewId(interviewId).orElse(null);
        if (b == null) {
            b = new Briefing();
            b.setInterviewId(interviewId);
        }
        b.setStatus(JobStatus.SUCCEEDED);
        b.setModel(response.getModel());
        if (!target.summary()) {
            // 질문만 다시 만든다: 요약과 만든 시각, 빠진 항목은 그대로 둔다.
            b.setPersonaId(personaId);
            briefingRepository.save(b);
            saveQuestions(interviewId, response);
            return;
        }
        b.setGeneratedAt(Times.now());
        b.setSummary(response.getSummary());
        if (target.questions()) {
            // 질문에 적용한 페르소나는 질문을 만들 때만 기록한다(요약만 다시 만들면 질문은 예전 페르소나 그대로다).
            b.setPersonaId(personaId);
        }
        // 빠진 항목 = 업로드 때 버린 블라인드 항목 + Agent 가 글에서 걸러 낸 항목
        Set<String> excluded = new LinkedHashSet<String>(ctx.getCandidate().getBlindedFields());
        if (response.getExcluded() != null) {
            excluded.addAll(response.getExcluded());
        }
        b.setExcluded(new ArrayList<String>(excluded));
        briefingRepository.save(b);
        if (target.questions()) {
            saveQuestions(interviewId, response);
        }
    }

    private void saveQuestions(Long interviewId, AgentDtos.BriefingResponse response) {
        List<QuestionService.Generated> generated = new ArrayList<QuestionService.Generated>();
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
                // 예상 꼬리질문(followUps)은 이 질문을 부모로 하는 FOLLOW_UP 질문으로 저장된다.
                generated.add(new QuestionService.Generated(q, g.getFollowUps()));
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
