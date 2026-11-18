package com.interview.adminstats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interview.agent.FakeAgentClient;
import com.interview.agent.dto.AgentDtos;
import com.interview.candidate.Candidate;
import com.interview.candidate.CandidateRepository;
import com.interview.common.Times;
import com.interview.common.json.Json;
import com.interview.evaluation.AiEvaluation;
import com.interview.evaluation.AiEvaluationRepository;
import com.interview.evaluation.InterviewerEvaluation;
import com.interview.evaluation.InterviewerEvaluationRepository;
import com.interview.interview.Interview;
import com.interview.interview.InterviewAssignee;
import com.interview.interview.InterviewAssigneeRepository;
import com.interview.interview.InterviewRepository;
import com.interview.interview.InterviewService;
import com.interview.interview.InterviewStatus;
import com.interview.interview.InterviewType;
import com.interview.interview.LiveProperties;
import com.interview.interview.MatchedBy;
import com.interview.job.AiJob;
import com.interview.job.AiJobRepository;
import com.interview.job.JobStatus;
import com.interview.job.JobType;
import com.interview.question.Question;
import com.interview.question.QuestionRepository;
import com.interview.question.QuestionSource;
import com.interview.recruitment.Track;
import com.interview.report.Report;
import com.interview.report.ReportBasis;
import com.interview.report.ReportDetail;
import com.interview.report.ReportRepository;
import com.interview.room.FollowUp;
import com.interview.room.FollowUpRepository;
import com.interview.room.Room;
import com.interview.room.RoomRepository;
import com.interview.room.RoomStatus;
import com.interview.room.Speaker;
import com.interview.room.SpeakerMethod;
import com.interview.room.TranscriptSource;
import com.interview.room.Utterance;
import com.interview.room.UtteranceRepository;
import com.interview.support.ApiTestSupport;
import com.interview.user.UserAccountRepository;

/**
 * 관리자 집계(API 명세 2.11). 테스트마다 새 채용을 만들고 작은 고정 데이터를 넣어 계산값을 확인한다.
 *
 * 데이터(면접 4건, 면접관 A=E1002 박준호, B=E1003 이하린, C=E1004 최민재):
 *  I1 EVALUATING  방 ENDED(25분 진행). 전사 4, 꼬리질문 4(채택 1), AI 평가(문제해결 4, 논리 2, 직무지식 점수 없음),
 *                 A 제출(3,4) · B 제출(5,4) · C 미제출, 보고서 LIVE(+40초) · REFINED(+300초), 마지막 보고서 작업 FAILED
 *  I2 IN_PROGRESS 60분 전 예정, 50분 전 시작(예정 30분) → 10분 지각, 20분 초과. 마지막 전사 10분 전
 *  I3 SCHEDULED   20분 전 예정인데 시작하지 않음
 *  I4 DONE        방 없음, 주의 없음
 */
class AdminStatsTest extends ApiTestSupport {

    @Autowired
    private FakeAgentClient fakeAgent;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private CandidateRepository candidateRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private InterviewAssigneeRepository assigneeRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private UtteranceRepository utteranceRepository;
    @Autowired
    private FollowUpRepository followUpRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private AiEvaluationRepository aiEvaluationRepository;
    @Autowired
    private InterviewerEvaluationRepository evaluationRepository;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private AiJobRepository jobRepository;

    private String admin;
    private long recruitmentId;
    private LocalDate date;
    private Interview i1;
    private Interview i2;
    private Interview i3;
    private Interview i4;
    private Room room2;
    private long userA;
    private long userB;
    private long userC;

    @Autowired
    private LiveProperties liveProps;

    private boolean newGradLiveBefore;

    @AfterEach
    void restoreLiveSetting() {
        liveProps.setNewGradEnabled(newGradLiveBefore);
    }

    @BeforeEach
    void setUp() throws Exception {
        // 이 테스트의 채용은 신입이다. 전사·마이크·AI 작업 관련 주의까지 보려면 신입의 실시간 기능을 켜야 한다
        // (기본은 꺼져 있다. 꺼졌을 때의 동작은 operationsSkipsLiveAlertsWhenLiveIsDisabled 가 본다).
        newGradLiveBefore = liveProps.isNewGradEnabled();
        liveProps.setNewGradEnabled(true);
        fakeAgent.setFakeStats(null);
        fakeAgent.setStatsDown(false);
        admin = login("E1001");
        recruitmentId = api(HttpMethod.POST, admin, "/api/v1/recruitments", "{\"title\":\"집계 테스트\",\"track\":\"NEW_GRAD\"}")
                .expect(200).json.get("id").asLong();
        userA = userRepository.findByEmployeeNo("E1002").get().getId();
        userB = userRepository.findByEmployeeNo("E1003").get().getId();
        userC = userRepository.findByEmployeeNo("E1004").get().getId();

        LocalDateTime now = Times.now();
        LocalDateTime i3At = now.minusMinutes(20);
        date = i3At.toLocalDate();
        LocalDateTime day = date.atStartOfDay();

        i1 = interview("ST-1", InterviewStatus.EVALUATING, day.plusHours(9));
        i2 = interview("ST-2", InterviewStatus.IN_PROGRESS, now.minusMinutes(60));
        i3 = interview("ST-3", InterviewStatus.SCHEDULED, i3At);
        i4 = interview("ST-4", InterviewStatus.DONE, day.plusHours(8));
        assign(i1, userA, userB, userC);
        assign(i2, userA, userB);
        assign(i3, userA);

        // I1: 끝난 방
        Room room1 = room(i1, RoomStatus.ENDED, day.plusHours(9), day.plusHours(9).plusMinutes(25));
        Question q1 = question(i1, QuestionSource.JD, true);
        question(i1, QuestionSource.RESUME, true);
        question(i1, QuestionSource.BANK, false);
        question(i1, QuestionSource.MANUAL, true); // 준비된 질문이 아니다
        utterance(room1, 1, Speaker.INTERVIEWER, SpeakerMethod.QUESTION_MATCH, q1.getId(), day.plusHours(9).plusMinutes(1));
        utterance(room1, 2, Speaker.CANDIDATE, SpeakerMethod.RULE, null, day.plusHours(9).plusMinutes(2));
        utterance(room1, 3, Speaker.CANDIDATE, SpeakerMethod.MANUAL, null, day.plusHours(9).plusMinutes(3));
        utterance(room1, 4, Speaker.UNKNOWN, SpeakerMethod.NONE, null, day.plusHours(9).plusMinutes(4));
        for (int n = 0; n < 4; n++) {
            FollowUp f = new FollowUp();
            f.setRoomId(room1.getId());
            f.setBasedOnSeq(2);
            f.setText("꼬리질문 " + n);
            f.setUsed(n == 0);
            f.setCreatedAt(now);
            followUpRepository.save(f);
        }
        AiEvaluation ai = new AiEvaluation();
        ai.setInterviewId(i1.getId());
        ai.setGeneratedAt(now);
        ai.setScores(scores(new Object[][] { { "PROBLEM_SOLVING", 4 }, { "LOGIC", 2 }, { "JOB_KNOWLEDGE", null } }));
        aiEvaluationRepository.save(ai);
        evaluation(i1, userA, true, "A", new Object[][] { { "PROBLEM_SOLVING", 3 }, { "LOGIC", 4 } });
        evaluation(i1, userB, true, "B", new Object[][] { { "PROBLEM_SOLVING", 5 }, { "LOGIC", 4 } });
        report(i1, 1, ReportBasis.LIVE, room1.getEndedAt().plusSeconds(40));
        report(i1, 2, ReportBasis.REFINED, room1.getEndedAt().plusSeconds(300));
        job(i1, JobType.AI_EVALUATION, JobStatus.SUCCEEDED);
        job(i1, JobType.REPORT, JobStatus.SUCCEEDED);
        job(i1, JobType.REPORT, JobStatus.FAILED);

        // I2: 진행 중인 방
        room2 = room(i2, RoomStatus.LIVE, now.minusMinutes(50), null);
        utterance(room2, 1, Speaker.CANDIDATE, SpeakerMethod.LLM, null, now.minusMinutes(10));
        evaluation(i2, userA, false, null, new Object[][] { { "PROBLEM_SOLVING", 1 } }); // 제출 전: 집계에 들어가지 않는다
    }

    @Test
    void onlyAdminCanReadAndRecruitmentIsRequired() throws Exception {
        String interviewer = login("E1003");
        for (String path : new String[] { "operations", "quality", "interviewer-stats" }) {
            String url = "/api/v1/admin/" + path;
            assertThat(apiGet(interviewer, url + "?recruitmentId=" + recruitmentId).expect(403).json.get("code").asText())
                    .isEqualTo("FORBIDDEN");
            apiGet(null, url + "?recruitmentId=" + recruitmentId).expect(401);
            apiGet(admin, url).expect(400);
            assertThat(apiGet(admin, url + "?recruitmentId=99999999").expect(404).json.get("code").asText())
                    .isEqualTo("RECRUITMENT_NOT_FOUND");
            apiGet(admin, url + "?recruitmentId=" + recruitmentId).expect(200);
        }
    }

    @Test
    void operationsShowsTimingsAndAlerts() throws Exception {
        JsonNode body = apiGet(admin, "/api/v1/admin/operations?recruitmentId=" + recruitmentId + "&date=" + date).expect(200).json;
        assertThat(body.get("date").asText()).isEqualTo(date.toString());
        assertThat(body.get("generatedAt").asText()).isNotEmpty();
        assertThat(body.get("agent").get("status").asText()).isEqualTo("UP");
        assertThat(body.get("agent").get("rooms").asInt()).isZero();

        JsonNode summary = body.get("summary");
        assertThat(summary.get("total").asInt()).isEqualTo(4);
        assertThat(summary.get("waiting").asInt()).isZero();
        assertThat(summary.get("scheduled").asInt()).isEqualTo(1);
        assertThat(summary.get("live").asInt()).isEqualTo(1);
        assertThat(summary.get("evaluating").asInt()).isEqualTo(1);
        assertThat(summary.get("done").asInt()).isEqualTo(1);
        assertThat(summary.get("attention").asInt()).isEqualTo(3);

        JsonNode items = body.get("items");
        assertThat(items).hasSize(4);
        // 주의가 있는 것이 먼저, 주의가 없는 I4 가 맨 뒤
        assertThat(items.get(3).get("interview").get("id").asLong()).isEqualTo(i4.getId());
        assertThat(items.get(3).get("alerts")).isEmpty();
        assertThat(items.get(3).get("elapsedMinutes").isNull()).isTrue();
        Map<Long, JsonNode> byId = byInterviewId(items);

        JsonNode ended = byId.get(i1.getId());
        assertThat(ended.get("interview").get("status").asText()).isEqualTo("EVALUATING");
        assertThat(ended.get("interview").get("candidate").get("applicantNo").asText()).isEqualTo("ST-1");
        assertThat(ended.get("elapsedMinutes").asInt()).isEqualTo(25);
        assertThat(ended.get("lateMinutes").asInt()).isZero();
        assertThat(ended.get("overtimeMinutes").asInt()).isZero();
        assertThat(ended.get("utteranceCount").asInt()).isEqualTo(4);
        assertThat(ended.get("endedAt").isNull()).isFalse();
        assertThat(ended.get("silentMinutes").asInt()).isZero();
        assertThat(texts(ended.get("alerts"))).containsExactly("JOB_FAILED");

        JsonNode live = byId.get(i2.getId());
        assertThat(live.get("elapsedMinutes").asInt()).isEqualTo(50);
        assertThat(live.get("lateMinutes").asInt()).isEqualTo(10);
        assertThat(live.get("overtimeMinutes").asInt()).isEqualTo(20);
        assertThat(live.get("utteranceCount").asInt()).isEqualTo(1);
        assertThat(live.get("lastUtteranceAt").isNull()).isFalse();
        assertThat(live.get("silentMinutes").asInt()).isEqualTo(10);
        assertThat(live.get("endedAt").isNull()).isTrue();
        // Agent 가 그 방을 모르면 audioConnected 는 null 이고 NO_AUDIO 를 붙이지 않는다.
        assertThat(live.get("audioConnected").isNull()).isTrue();
        assertThat(texts(live.get("alerts"))).containsExactly("OVERTIME", "SILENT");

        JsonNode notStarted = byId.get(i3.getId());
        assertThat(notStarted.get("startedAt").isNull()).isTrue();
        assertThat(notStarted.get("lateMinutes").asInt()).isEqualTo(20);
        assertThat(texts(notStarted.get("alerts"))).containsExactly("NOT_STARTED");
    }

    @Test
    void operationsSkipsLiveAlertsWhenLiveIsDisabled() throws Exception {
        // 실시간 기능을 쓰지 않는 면접: 전사·마이크·Agent·AI 작업을 전제로 한 주의는 붙지 않고, 시작 지연·시간 초과만 붙는다.
        liveProps.setNewGradEnabled(false);
        String url = "/api/v1/admin/operations?recruitmentId=" + recruitmentId + "&date=" + date;
        try {
            // Agent 가 그 방의 마이크가 끊겼고 큐가 밀렸다고 해도 붙지 않는다.
            AgentDtos.StatsResponse stats = new AgentDtos.StatsResponse();
            AgentDtos.RoomStat roomStat = new AgentDtos.RoomStat();
            roomStat.setRoomId(room2.getId());
            roomStat.setQueueDepth(7);
            roomStat.setAudioConnected(false);
            stats.getRoomStats().add(roomStat);
            fakeAgent.setFakeStats(stats);

            JsonNode body = apiGet(admin, url).expect(200).json;
            Map<Long, JsonNode> byId = byInterviewId(body.get("items"));
            assertThat(byId.get(i1.getId()).get("interview").get("liveEnabled").asBoolean()).isFalse();
            assertThat(byId.get(i1.getId()).get("alerts")).isEmpty();                       // JOB_FAILED 없음
            JsonNode live = byId.get(i2.getId());
            assertThat(texts(live.get("alerts"))).containsExactly("OVERTIME");               // SILENT·NO_AUDIO·STT_BACKLOG 없음
            assertThat(live.get("overtimeMinutes").asInt()).isEqualTo(20);
            assertThat(live.get("lateMinutes").asInt()).isEqualTo(10);
            assertThat(live.get("silentMinutes").asInt()).isZero();
            assertThat(texts(byId.get(i3.getId()).get("alerts"))).containsExactly("NOT_STARTED");
            // 주의가 있는 면접 수에도 세지 않는다: 시간 초과(I2)와 시작 지연(I3)뿐이다.
            assertThat(body.get("summary").get("attention").asInt()).isEqualTo(2);
            assertThat(body.get("summary").get("total").asInt()).isEqualTo(4);

            // Agent 가 죽어 있어도 AGENT_DOWN 이 붙지 않는다.
            fakeAgent.setStatsDown(true);
            JsonNode down = apiGet(admin, url).expect(200).json;
            assertThat(down.get("agent").get("status").asText()).isEqualTo("DOWN");
            assertThat(texts(byInterviewId(down.get("items")).get(i2.getId()).get("alerts"))).containsExactly("OVERTIME");
            assertThat(down.get("summary").get("attention").asInt()).isEqualTo(2);
        } finally {
            fakeAgent.setFakeStats(null);
            fakeAgent.setStatsDown(false);
        }
    }

    @Test
    void operationsMergesAgentRoomStatsAndSurvivesAgentDown() throws Exception {
        String url = "/api/v1/admin/operations?recruitmentId=" + recruitmentId + "&date=" + date;
        try {
            AgentDtos.StatsResponse stats = new AgentDtos.StatsResponse();
            stats.setRooms(3);
            AgentDtos.ProviderStat provider = new AgentDtos.ProviderStat();
            provider.setHealth("DEGRADED");
            provider.setWaiting(2);
            stats.getProviders().put("stt.live", provider);
            AgentDtos.RoomStat roomStat = new AgentDtos.RoomStat();
            roomStat.setRoomId(room2.getId());
            roomStat.setQueueDepth(7);
            roomStat.setAudioConnected(false);
            stats.getRoomStats().add(roomStat);
            fakeAgent.setFakeStats(stats);

            JsonNode body = apiGet(admin, url).expect(200).json;
            assertThat(body.get("agent").get("rooms").asInt()).isEqualTo(3);
            JsonNode provided = body.get("agent").get("providers").get(0);
            assertThat(provided.get("role").asText()).isEqualTo("stt.live");
            assertThat(provided.get("health").asText()).isEqualTo("DEGRADED");
            assertThat(provided.get("waiting").asInt()).isEqualTo(2);
            JsonNode live = byInterviewId(body.get("items")).get(i2.getId());
            assertThat(live.get("audioConnected").asBoolean()).isFalse();
            assertThat(live.get("queueDepth").asInt()).isEqualTo(7);
            assertThat(texts(live.get("alerts"))).containsExactly("OVERTIME", "NO_AUDIO", "SILENT", "STT_BACKLOG");

            // Agent 가 죽어 있어도 200 이고, 진행 중인 방에만 AGENT_DOWN 이 붙는다.
            fakeAgent.setStatsDown(true);
            JsonNode down = apiGet(admin, url).expect(200).json;
            assertThat(down.get("agent").get("status").asText()).isEqualTo("DOWN");
            assertThat(down.get("agent").get("rooms").isNull()).isTrue();
            assertThat(down.get("agent").get("providers")).isEmpty();
            assertThat(down.get("summary").get("total").asInt()).isEqualTo(4);
            Map<Long, JsonNode> byId = byInterviewId(down.get("items"));
            assertThat(byId.get(i2.getId()).get("audioConnected").isNull()).isTrue();
            assertThat(texts(byId.get(i2.getId()).get("alerts"))).containsExactly("OVERTIME", "SILENT", "AGENT_DOWN");
            assertThat(texts(byId.get(i1.getId()).get("alerts"))).containsExactly("JOB_FAILED");
            assertThat(texts(byId.get(i3.getId()).get("alerts"))).containsExactly("NOT_STARTED");
        } finally {
            fakeAgent.setFakeStats(null);
            fakeAgent.setStatsDown(false);
        }
    }

    @Test
    void qualityComparesAiWithInterviewersAndCountsUsage() throws Exception {
        JsonNode body = apiGet(admin, "/api/v1/admin/quality?recruitmentId=" + recruitmentId).expect(200).json;
        assertThat(body.get("endedInterviewCount").asInt()).isEqualTo(1);

        // 문제해결: AI 4 vs 면접관 평균 (3+5)/2 = 4 → 0,  논리: AI 2 vs 4 → -2,  직무지식: AI 점수 없음 → 비교 제외
        JsonNode gap = body.get("scoreGap");
        assertThat(gap.get("comparedCount").asInt()).isEqualTo(2);
        assertThat(gap.get("meanDiff").asDouble()).isEqualTo(-1.0);
        assertThat(gap.get("meanAbsDiff").asDouble()).isEqualTo(1.0);
        assertThat(gap.get("withinOneRate").asDouble()).isEqualTo(0.5);
        assertThat(gap.get("byCriterion")).hasSize(2);
        JsonNode logic = gap.get("byCriterion").get(0);
        assertThat(logic.get("criterionCode").asText()).isEqualTo("LOGIC");
        assertThat(logic.get("criterionName").asText()).isEqualTo("논리성");
        assertThat(logic.get("comparedCount").asInt()).isEqualTo(1);
        assertThat(logic.get("aiMean").asDouble()).isEqualTo(2.0);
        assertThat(logic.get("interviewerMean").asDouble()).isEqualTo(4.0);
        assertThat(logic.get("meanDiff").asDouble()).isEqualTo(-2.0);
        assertThat(logic.get("meanAbsDiff").asDouble()).isEqualTo(2.0);
        assertThat(gap.get("byCriterion").get(1).get("criterionCode").asText()).isEqualTo("PROBLEM_SOLVING");
        assertThat(gap.get("byCriterion").get(1).get("meanDiff").asDouble()).isEqualTo(0.0);

        // 신입 직무 면접의 평가 항목은 4개, AI 가 점수를 낸 것은 2개
        assertThat(body.get("aiCoverage").get("expected").asInt()).isEqualTo(4);
        assertThat(body.get("aiCoverage").get("scored").asInt()).isEqualTo(2);
        assertThat(body.get("aiCoverage").get("rate").asDouble()).isEqualTo(0.5);

        assertThat(body.get("followUps").get("suggested").asInt()).isEqualTo(4);
        assertThat(body.get("followUps").get("used").asInt()).isEqualTo(1);
        assertThat(body.get("followUps").get("adoptionRate").asDouble()).isEqualTo(0.25);
        assertThat(body.get("followUps").get("roomCount").asInt()).isEqualTo(1);

        JsonNode speaker = body.get("speaker");
        assertThat(speaker.get("utteranceCount").asInt()).isEqualTo(5);
        assertThat(speaker.get("byMethod").get("MANUAL").asInt()).isEqualTo(1);
        assertThat(speaker.get("byMethod").get("QUESTION_MATCH").asInt()).isEqualTo(1);
        assertThat(speaker.get("byMethod").get("RULE").asInt()).isEqualTo(1);
        assertThat(speaker.get("byMethod").get("LLM").asInt()).isEqualTo(1);
        assertThat(speaker.get("byMethod").get("CHANNEL").asInt()).isZero();
        assertThat(speaker.get("byMethod").get("NONE").asInt()).isEqualTo(1);
        assertThat(speaker.get("manualRate").asDouble()).isEqualTo(0.2);
        assertThat(speaker.get("unknownRate").asDouble()).isEqualTo(0.2);

        // 준비된 질문 3(JD, RESUME, BANK), 물은 것 2, 전사에서 자동으로 걸린 것 1
        JsonNode questions = body.get("questions");
        assertThat(questions.get("prepared").asInt()).isEqualTo(3);
        assertThat(questions.get("asked").asInt()).isEqualTo(2);
        assertThat(questions.get("askedRate").asDouble()).isEqualTo(0.67);
        assertThat(questions.get("autoMatched").asInt()).isEqualTo(1);
        assertThat(questions.get("autoMatchedRate").asDouble()).isEqualTo(0.5);
        // 출처별: JD 1개 중 1개, RESUME 1개 중 1개, BANK 1개 중 0개. 직접 추가(MANUAL)는 들어가지 않는다.
        JsonNode bySource = questions.get("bySource");
        assertThat(bySource).hasSize(3);
        assertThat(bySource.get(0).get("source").asText()).isEqualTo("JD");
        assertThat(bySource.get(0).get("prepared").asInt()).isEqualTo(1);
        assertThat(bySource.get(0).get("askedRate").asDouble()).isEqualTo(1.0);
        assertThat(bySource.get(1).get("source").asText()).isEqualTo("RESUME");
        assertThat(bySource.get(2).get("source").asText()).isEqualTo("BANK");
        assertThat(bySource.get(2).get("asked").asInt()).isZero();
        assertThat(bySource.get(2).get("askedRate").asDouble()).isEqualTo(0.0);

        JsonNode reports = body.get("reports");
        assertThat(reports.get("liveCount").asInt()).isEqualTo(1);
        assertThat(reports.get("refinedCount").asInt()).isEqualTo(1);
        assertThat(reports.get("liveSecondsMedian").asInt()).isEqualTo(40);
        assertThat(reports.get("liveSecondsMax").asInt()).isEqualTo(40);
        assertThat(reports.get("refinedSecondsMedian").asInt()).isEqualTo(300);
        assertThat(reports.get("refinedSecondsMax").asInt()).isEqualTo(300);

        Map<String, JsonNode> jobs = new HashMap<String, JsonNode>();
        for (JsonNode job : body.get("jobs")) {
            jobs.put(job.get("type").asText(), job);
        }
        assertThat(jobs.get("REPORT").get("succeeded").asInt()).isEqualTo(1);
        assertThat(jobs.get("REPORT").get("failed").asInt()).isEqualTo(1);
        assertThat(jobs.get("AI_EVALUATION").get("succeeded").asInt()).isEqualTo(1);
        assertThat(jobs.get("BRIEFING").get("succeeded").asInt()).isZero();
    }

    @Test
    void qualityOfEmptyRecruitmentHasNullRates() throws Exception {
        long empty = api(HttpMethod.POST, admin, "/api/v1/recruitments", "{\"title\":\"빈 채용\",\"track\":\"EXPERIENCED\"}").expect(200).json
                .get("id").asLong();
        JsonNode body = apiGet(admin, "/api/v1/admin/quality?recruitmentId=" + empty).expect(200).json;
        assertThat(body.get("scoreGap").get("comparedCount").asInt()).isZero();
        assertThat(body.get("scoreGap").get("meanDiff").isNull()).isTrue();
        assertThat(body.get("aiCoverage").get("rate").isNull()).isTrue();
        assertThat(body.get("followUps").get("adoptionRate").isNull()).isTrue();
        assertThat(body.get("speaker").get("manualRate").isNull()).isTrue();
        assertThat(body.get("questions").get("askedRate").isNull()).isTrue();
        assertThat(body.get("reports").get("liveSecondsMedian").isNull()).isTrue();
        JsonNode stats = apiGet(admin, "/api/v1/admin/interviewer-stats?recruitmentId=" + empty).expect(200).json;
        assertThat(stats.get("overall").get("meanScore").isNull()).isTrue();
        assertThat(stats.get("items")).isEmpty();
        assertThat(apiGet(admin, "/api/v1/admin/operations?recruitmentId=" + empty).expect(200).json.get("items")).isEmpty();
    }

    @Test
    void interviewerStatsShowsDeviation() throws Exception {
        JsonNode body = apiGet(admin, "/api/v1/admin/interviewer-stats?recruitmentId=" + recruitmentId).expect(200).json;
        // 제출된 평가 2건의 점수 3,4,5,4 → 평균 4.0, 모표준편차 sqrt(0.5) = 0.71
        assertThat(body.get("overall").get("evaluationCount").asInt()).isEqualTo(2);
        assertThat(body.get("overall").get("meanScore").asDouble()).isEqualTo(4.0);
        assertThat(body.get("overall").get("stdDev").asDouble()).isEqualTo(0.71);

        JsonNode items = body.get("items");
        assertThat(items).hasSize(3);
        // 제출이 많은 순(같으면 이름 순): 박준호(1), 이하린(1), 최민재(0)
        JsonNode a = items.get(0);
        assertThat(a.get("interviewer").get("id").asLong()).isEqualTo(userA);
        assertThat(a.get("interviewer").get("name").asText()).isEqualTo("박준호");
        assertThat(a.get("interviewer").get("department").asText()).isEqualTo("SW개발1팀");
        assertThat(a.get("assignedCount").asInt()).isEqualTo(3);
        assertThat(a.get("submittedCount").asInt()).isEqualTo(1);
        assertThat(a.get("pendingCount").asInt()).isZero();
        assertThat(a.get("meanScore").asDouble()).isEqualTo(3.5);
        assertThat(a.get("stdDev").asDouble()).isEqualTo(0.5);
        assertThat(a.get("deviationFromOverall").asDouble()).isEqualTo(-0.5);
        assertThat(a.get("deviationFromPeers").asDouble()).isEqualTo(-1.0); // 내 평균 3.5 - 이하린의 평균 4.5
        assertThat(a.get("peerComparedCount").asInt()).isEqualTo(1);
        assertThat(a.get("scoreDistribution").toString()).isEqualTo("{\"1\":0,\"2\":0,\"3\":1,\"4\":1,\"5\":0}");
        assertThat(a.get("gradeDistribution").toString()).isEqualTo("{\"A\":1}");
        assertThat(a.get("byCriterion")).hasSize(2);
        assertThat(a.get("byCriterion").get(0).get("criterionCode").asText()).isEqualTo("LOGIC");
        assertThat(a.get("byCriterion").get(0).get("meanScore").asDouble()).isEqualTo(4.0);
        assertThat(a.get("byCriterion").get(1).get("criterionName").asText()).isEqualTo("문제 해결");
        assertThat(a.get("byCriterion").get(1).get("meanScore").asDouble()).isEqualTo(3.0);

        JsonNode b = items.get(1);
        assertThat(b.get("interviewer").get("id").asLong()).isEqualTo(userB);
        assertThat(b.get("assignedCount").asInt()).isEqualTo(2);
        assertThat(b.get("meanScore").asDouble()).isEqualTo(4.5);
        assertThat(b.get("deviationFromOverall").asDouble()).isEqualTo(0.5);
        assertThat(b.get("deviationFromPeers").asDouble()).isEqualTo(1.0);

        // 배정만 있고 제출이 없는 면접관: 평균은 null, 끝난 면접의 미제출은 pending
        JsonNode c = items.get(2);
        assertThat(c.get("interviewer").get("id").asLong()).isEqualTo(userC);
        assertThat(c.get("assignedCount").asInt()).isEqualTo(1);
        assertThat(c.get("submittedCount").asInt()).isZero();
        assertThat(c.get("pendingCount").asInt()).isEqualTo(1);
        assertThat(c.get("meanScore").isNull()).isTrue();
        assertThat(c.get("stdDev").isNull()).isTrue();
        assertThat(c.get("deviationFromOverall").isNull()).isTrue();
        assertThat(c.get("deviationFromPeers").isNull()).isTrue();
        assertThat(c.get("peerComparedCount").asInt()).isZero();
    }

    // ------------------------------------------------------------------ 데이터 만들기

    private Interview interview(String applicantNo, InterviewStatus status, LocalDateTime scheduledAt) {
        Candidate c = new Candidate();
        c.setRecruitmentId(recruitmentId);
        c.setApplicantNo(applicantNo);
        c.setName("집계" + applicantNo);
        c.setTrack(Track.NEW_GRAD);
        c.setJobCode("SW");
        c.setJobTitle("SW개발");
        c.setCreatedAt(Times.now());
        candidateRepository.save(c);
        Interview i = InterviewService.newInterview(c, InterviewType.JOB, 1);
        i.setStatus(status);
        i.setScheduledAt(scheduledAt);
        return interviewRepository.save(i);
    }

    private void assign(Interview i, long... userIds) {
        for (long userId : userIds) {
            assigneeRepository.save(new InterviewAssignee(i.getId(), userId, MatchedBy.MANUAL));
        }
    }

    private Room room(Interview i, RoomStatus status, LocalDateTime startedAt, LocalDateTime endedAt) {
        Room room = new Room();
        room.setInterviewId(i.getId());
        room.setStatus(status);
        room.setCreatedAt(startedAt);
        room.setStartedAt(startedAt);
        room.setEndedAt(endedAt);
        return roomRepository.save(room);
    }

    private Question question(Interview i, QuestionSource source, boolean asked) {
        Question q = new Question();
        q.setInterviewId(i.getId());
        q.setSource(source);
        q.setText("질문");
        q.setAsked(asked);
        q.setCreatedAt(Times.now());
        return questionRepository.save(q);
    }

    private void utterance(Room room, int seq, Speaker speaker, SpeakerMethod method, Long matchedQuestionId,
            LocalDateTime savedAt) {
        Utterance u = new Utterance();
        u.setRoomId(room.getId());
        u.setSeq(seq);
        u.setSpeaker(speaker);
        u.setSpeakerMethod(method);
        u.setRawText("발화");
        u.setMatchedQuestionId(matchedQuestionId);
        u.setSource(TranscriptSource.LIVE);
        u.setSavedAt(savedAt);
        utteranceRepository.save(u);
    }

    private void evaluation(Interview i, long userId, boolean submitted, String grade, Object[][] scores) {
        InterviewerEvaluation e = new InterviewerEvaluation();
        e.setInterviewId(i.getId());
        e.setUserId(userId);
        e.setScores(scores(scores));
        e.setGrade(grade);
        e.setSubmitted(submitted);
        evaluationRepository.save(e);
    }

    private void report(Interview i, int version, ReportBasis basis, LocalDateTime generatedAt) {
        Report r = new Report();
        r.setInterviewId(i.getId());
        r.setVersion(version);
        r.setBasis(basis);
        r.setDetail(ReportDetail.BRIEF);
        r.setGeneratedAt(generatedAt);
        r.setContent(Json.object().put("headline", "테스트"));
        reportRepository.save(r);
    }

    private void job(Interview i, JobType type, JobStatus status) {
        AiJob job = new AiJob();
        job.setType(type);
        job.setStatus(status);
        job.setInterviewId(i.getId());
        job.setWorkerId("another-server"); // 재시작 복구 테스트가 건드리지 않게
        job.setCreatedAt(Times.now());
        jobRepository.save(job);
    }

    private static ArrayNode scores(Object[][] rows) {
        ArrayNode array = Json.array();
        for (Object[] row : rows) {
            ObjectNode node = array.addObject();
            node.put("criterionCode", (String) row[0]);
            if (row[1] == null) {
                node.putNull("score");
            } else {
                node.put("score", (Integer) row[1]);
            }
        }
        return array;
    }

    private static Map<Long, JsonNode> byInterviewId(JsonNode items) {
        Map<Long, JsonNode> map = new HashMap<Long, JsonNode>();
        for (JsonNode item : items) {
            map.put(item.get("interview").get("id").asLong(), item);
        }
        return map;
    }

    private static List<String> texts(JsonNode array) {
        List<String> result = new ArrayList<String>();
        for (JsonNode node : array) {
            result.add(node.asText());
        }
        return result;
    }
}
