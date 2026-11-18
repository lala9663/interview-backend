package com.interview.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interview.agent.dto.AgentDtos;
import com.interview.briefing.BriefingProperties;
import com.interview.briefing.BriefingService;
import com.interview.interview.InterviewContext;
import com.interview.job.AiJob;
import com.interview.job.AiJobService;
import com.interview.job.JobType;
import com.interview.question.Question;
import com.interview.question.QuestionService;
import com.interview.questionbank.QuestionBankItem;
import com.interview.questionbank.QuestionBankService;
import com.interview.report.ReportBasis;
import com.interview.room.Room;
import com.interview.room.RoomService;
import com.interview.room.TranscriptService;

import lombok.RequiredArgsConstructor;

/** Agent 콜백의 업무 로직. 저장은 각 기능의 서비스에 맡긴다. */
@Service
@RequiredArgsConstructor
public class InternalRoomService {

    private final RoomService roomService;
    private final TranscriptService transcriptService;
    private final InterviewContext.Loader contextLoader;
    private final BriefingService briefingService;
    private final QuestionService questionService;
    private final QuestionBankService questionBankService;
    private final AiJobService jobService;
    private final BriefingProperties briefingProps;

    /** 방을 열 때 Agent 가 한 번 읽는 정보. */
    @Transactional(readOnly = true)
    public InternalDtos.RoomContext context(Long roomId) {
        Room room = roomService.requireRoom(roomId);
        InterviewContext ctx = contextLoader.load(room.getInterviewId());
        InternalDtos.RoomContext result = new InternalDtos.RoomContext();
        result.setRoomId(room.getId());
        result.setInterviewId(room.getInterviewId());
        result.setStatus(room.getStatus());
        result.setTrack(ctx.getInterview().getTrack());
        result.setInterviewType(ctx.getInterview().getType());
        result.setMode(ctx.getInterview().getMode());
        result.setJobTitle(ctx.getJobTitle());
        result.setJdText(ctx.getJdText());
        result.setCandidateBrief(briefingService.candidateBrief(room.getInterviewId()));
        result.setPersona(BriefingService.personaPart(ctx.getPersona()));
        for (Question q : questionService.list(room.getInterviewId())) {
            if (!q.isHidden()) {
                result.getQuestions().add(new InternalDtos.ContextQuestion(q.getId(), q.getSource(), q.getCategory(), q.getText()));
            }
        }
        for (QuestionBankItem item : questionBankService.usableFor(ctx.getCandidate().getJobCode(),
                ctx.getInterview().getType(), briefingProps.getQuestionBankLimit())) {
            result.getQuestionBank().add(new AgentDtos.BankItem(item.getId(), item.getCategory(), item.getText()));
        }
        return result;
    }

    /**
     * 사후 STT 보정 전사를 받아 전사를 바꾸고(source=POST), AI 평가 → REFINED 보고서 작업을 접수한다.
     *
     * <p>보정 전사는 발화 번호(seq)가 다시 매겨지고 화자도 다시 판정된다. 초안 기준 AI 평가의 근거 번호
     * (evidenceSeqs)가 다른 발화를 가리키게 되므로, 보고서만이 아니라 AI 평가도 보정 전사로 다시 한다.
     */
    @Transactional
    public AiJob refinedTranscript(Long roomId, InternalDtos.RefinedTranscriptRequest request) {
        Room room = roomService.requireRoom(roomId);
        transcriptService.replaceRefined(roomId, request.getSttModel(), request.getItems());
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("reportBasis", ReportBasis.REFINED.name());
        return jobService.submit(JobType.AI_EVALUATION, room.getInterviewId(), payload);
    }
}
