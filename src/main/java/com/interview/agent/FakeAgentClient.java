package com.interview.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interview.agent.dto.AgentDtos;
import com.interview.candidate.CandidateProfile;
import com.interview.common.json.Json;
import com.interview.report.ReportDetail;

/**
 * Agent 없이 도는 가짜 구현(app.agent.mode=fake). 테스트와 백엔드 단독 실행에 쓴다.
 * 입력으로부터 정해진 규칙으로 결과를 만든다(모델 호출 없음, model = "fake-llm").
 * 받은 요청을 기록해 두므로 테스트에서 "Agent 로 무엇이 나갔는지" 확인할 수 있다.
 */
@Component
@ConditionalOnProperty(name = "app.agent.mode", havingValue = "fake")
public class FakeAgentClient implements AgentClient {

    public static final String MODEL = "fake-llm";
    private static final int MAX_RECORDED = 200;

    private final List<Object> recorded = Collections.synchronizedList(new ArrayList<Object>());
    private final List<Long> finalizedRooms = Collections.synchronizedList(new ArrayList<Long>());

    /** 지금까지 받은 요청(오래된 순). */
    public List<Object> getRecorded() {
        synchronized (recorded) {
            return new ArrayList<Object>(recorded);
        }
    }

    public List<Long> getFinalizedRooms() {
        synchronized (finalizedRooms) {
            return new ArrayList<Long>(finalizedRooms);
        }
    }

    public void clearRecorded() {
        recorded.clear();
        finalizedRooms.clear();
    }

    private void record(Object request) {
        recorded.add(request);
        while (recorded.size() > MAX_RECORDED) {
            recorded.remove(0);
        }
    }

    @Override
    public boolean isUp() {
        return true;
    }

    @Override
    public AgentDtos.BriefingResponse createBriefing(AgentDtos.BriefingRequest request) {
        record(request);
        CandidateProfile profile = request.getCandidate() == null || request.getCandidate().getProfile() == null
                ? new CandidateProfile() : request.getCandidate().getProfile();
        String major = profile.getEducation().isEmpty() ? null : profile.getEducation().get(0).getMajor();

        ObjectNode summary = Json.object();
        summary.put("headline", (major == null ? "" : major + " 전공의 ") + request.getJobTitle() + " 지원자");
        ArrayNode keyPoints = summary.putArray("keyPoints");
        if (major != null) {
            keyPoints.add("전공: " + major);
        }
        for (CandidateProfile.TitledText p : profile.getProjects()) {
            keyPoints.add("프로젝트: " + cut(p.getText(), 60));
        }
        if (!profile.getCertificates().isEmpty()) {
            keyPoints.add("자격증: " + profile.getCertificates());
        }
        summary.putArray("strengths").add("직무와 관련된 프로젝트 경험이 있습니다.");
        summary.putArray("checkPoints").add("프로젝트에서 본인이 직접 한 일의 범위");
        ArrayNode timeline = summary.putArray("timeline");
        for (CandidateProfile.Career c : profile.getCareers()) {
            ObjectNode item = timeline.addObject();
            item.put("period", c.getPeriod() == null ? "" : c.getPeriod());
            item.put("text", cut(c.getText(), 60));
        }

        AgentDtos.BriefingResponse response = new AgentDtos.BriefingResponse();
        response.setSummary(summary);
        int jd = request.getQuestionCount() == null ? 4 : request.getQuestionCount().getJd();
        int resume = request.getQuestionCount() == null ? 6 : request.getQuestionCount().getResume();
        for (int i = 1; i <= jd; i++) {
            response.getQuestions().add(new AgentDtos.GeneratedQuestion("JD", "직무",
                    request.getJobTitle() + " 직무에서 중요하다고 보는 역량 " + i + "번은 무엇이고, 어떻게 길러 왔습니까?", "직무 이해도 확인"));
        }
        for (int i = 1; i <= resume; i++) {
            String basis;
            if (!profile.getEssays().isEmpty()) {
                basis = "자기소개서의 '" + profile.getEssays().get((i - 1) % profile.getEssays().size()).getTitle() + "'";
            } else {
                basis = "이력서";
            }
            response.getQuestions().add(new AgentDtos.GeneratedQuestion("RESUME", "경험",
                    basis + "에 적은 내용 중 " + i + "번째로 강조하고 싶은 경험을 구체적으로 설명해 주세요.", "서류 내용의 사실 확인"));
        }
        int bankCount = 0;
        for (AgentDtos.BankItem b : request.getQuestionBank()) {
            if (bankCount++ >= 2) {
                break;
            }
            response.getQuestions().add(new AgentDtos.GeneratedQuestion("BANK", b.getCategory(), b.getText(), "질문 은행에서 고름"));
        }
        response.setModel(MODEL);
        return response;
    }

    @Override
    public AgentDtos.EvaluationResponse evaluate(AgentDtos.EvaluationRequest request) {
        record(request);
        List<Integer> candidateSeqs = new ArrayList<Integer>();
        for (AgentDtos.TranscriptLine line : request.getTranscript()) {
            if ("CANDIDATE".equals(line.getSpeaker()) && candidateSeqs.size() < 2) {
                candidateSeqs.add(line.getSeq());
            }
        }
        AgentDtos.EvaluationResponse response = new AgentDtos.EvaluationResponse();
        int index = 0;
        for (AgentDtos.CriterionItem c : request.getCriteria()) {
            int score = Math.max(1, Math.min(c.getMaxScore(), c.getMaxScore() - 1 - (index++ % 2)));
            response.getScores().add(new AgentDtos.ScoreItem(c.getCode(), score,
                    c.getName() + " 항목은 답변 " + candidateSeqs + " 을 근거로 판단했습니다. (참고용 가짜 평가)",
                    new ArrayList<Integer>(candidateSeqs)));
        }
        response.setOverallComment("전사 " + request.getTranscript().size() + "개 발화를 바탕으로 한 참고 평가입니다. (가짜 모델)");
        response.setModel(MODEL);
        return response;
    }

    @Override
    public AgentDtos.ReportResponse createReport(AgentDtos.ReportRequest request) {
        record(request);
        boolean detailed = request.getDetail() == ReportDetail.DETAILED;
        ObjectNode content = Json.object();
        content.put("headline", request.getJobTitle() + " " + request.getInterviewType() + " 면접 총평 (" + request.getBasis() + ")");
        content.put("overallComment", "전사 " + request.getTranscript().size() + "개 발화 기준입니다. 질문 의도에 맞게 답했고, "
                + "경험을 구체적인 수치로 설명했습니다. 일부 답변은 본인 기여 범위가 불분명했습니다. (가짜 모델)");
        content.putArray("strengths").add("경험을 구체적으로 설명함").add("질문 의도를 정확히 파악함");
        content.putArray("concerns").add("본인 기여 범위가 불분명한 답변이 있음");
        ArrayNode qa = content.putArray("qaHighlights");
        AgentDtos.TranscriptLine lastQuestion = null;
        for (AgentDtos.TranscriptLine line : request.getTranscript()) {
            if ("INTERVIEWER".equals(line.getSpeaker())) {
                lastQuestion = line;
            } else if ("CANDIDATE".equals(line.getSpeaker()) && lastQuestion != null && qa.size() < 3) {
                ObjectNode item = qa.addObject();
                item.put("question", lastQuestion.getText());
                item.put("answerSummary", cut(line.getText(), 80));
                item.putArray("seqs").add(lastQuestion.getSeq()).add(line.getSeq());
                lastQuestion = null;
            }
        }
        ArrayNode competencies = content.putArray("competencies");
        ArrayNode careerChecks = content.putArray("careerChecks");
        ArrayNode nextRoundChecks = content.putArray("nextRoundChecks");
        ArrayNode risks = content.putArray("risks");
        if (detailed) {
            ObjectNode comp = competencies.addObject();
            comp.put("name", "직무 전문성");
            comp.put("assessment", "담당했던 업무를 구조적으로 설명했습니다.");
            comp.putArray("evidence").add("전사에서 확인된 답변");
            ObjectNode check = careerChecks.addObject();
            check.put("claim", "이력서의 주요 프로젝트를 주도했다는 주장");
            check.put("finding", "면접에서 역할과 의사결정 과정을 설명해 대체로 확인됨");
            nextRoundChecks.add("협업 방식과 갈등 해결 사례");
            for (AgentDtos.PreviousRound prev : request.getPreviousRounds()) {
                nextRoundChecks.add("이전 차수(" + prev.getType() + ")에서 남은 확인 사항 재검토");
            }
            risks.add("이직 사유에 대한 설명이 짧았음");
        }
        AgentDtos.ReportResponse response = new AgentDtos.ReportResponse();
        response.setContent(content);
        response.setModel(MODEL);
        return response;
    }

    @Override
    public AgentDtos.FinalizeResponse finalizeRoom(Long roomId, boolean postStt) {
        finalizedRooms.add(roomId);
        return new AgentDtos.FinalizeResponse(true, postStt, "MEMORY_ONLY");
    }

    private volatile AgentDtos.StatsResponse fakeStats;
    private volatile boolean statsDown;

    /** 테스트용: stats() 가 돌려줄 값을 정한다. null 이면 기본값(방 없음). */
    public void setFakeStats(AgentDtos.StatsResponse stats) {
        this.fakeStats = stats;
    }

    /** 테스트용: true 면 stats() 가 Agent 가 죽은 것처럼 실패한다. */
    public void setStatsDown(boolean statsDown) {
        this.statsDown = statsDown;
    }

    @Override
    public AgentDtos.StatsResponse stats() {
        if (statsDown) {
            throw new AgentException(AgentException.AGENT_UNAVAILABLE, "Agent 통계를 읽을 수 없습니다. (가짜 장애)", null);
        }
        AgentDtos.StatsResponse stats = fakeStats;
        if (stats != null) {
            return stats;
        }
        AgentDtos.StatsResponse empty = new AgentDtos.StatsResponse();
        empty.setInstanceId("fake-agent");
        return empty;
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
