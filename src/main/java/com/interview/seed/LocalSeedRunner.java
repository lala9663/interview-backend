package com.interview.seed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interview.briefing.Briefing;
import com.interview.briefing.BriefingRepository;
import com.interview.candidate.ApplicantType;
import com.interview.candidate.Candidate;
import com.interview.candidate.CandidateProfile;
import com.interview.candidate.CandidateRepository;
import com.interview.common.Times;
import com.interview.common.json.Json;
import com.interview.evaluation.AiEvaluation;
import com.interview.evaluation.AiEvaluationRepository;
import com.interview.evaluation.EvalCriterion;
import com.interview.evaluation.EvalCriterionRepository;
import com.interview.evaluation.InterviewerEvaluation;
import com.interview.evaluation.InterviewerEvaluationRepository;
import com.interview.interview.Interview;
import com.interview.interview.InterviewAssignee;
import com.interview.interview.InterviewAssigneeRepository;
import com.interview.interview.InterviewRepository;
import com.interview.interview.InterviewService;
import com.interview.interview.InterviewStatus;
import com.interview.interview.InterviewType;
import com.interview.interview.MatchedBy;
import com.interview.job.AiJob;
import com.interview.job.AiJobRepository;
import com.interview.job.JobStatus;
import com.interview.job.JobType;
import com.interview.question.Question;
import com.interview.question.QuestionRepository;
import com.interview.question.QuestionService;
import com.interview.question.QuestionSource;
import com.interview.questionbank.QuestionBankItem;
import com.interview.questionbank.QuestionBankRepository;
import com.interview.recruitment.JobPosting;
import com.interview.recruitment.JobPostingRepository;
import com.interview.recruitment.Recruitment;
import com.interview.recruitment.RecruitmentRepository;
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
import com.interview.user.UserAccount;
import com.interview.user.UserAccountRepository;
import com.interview.user.UserRole;

import lombok.RequiredArgsConstructor;

/**
 * local 프로파일의 시드 데이터. 모두 가짜 데이터다(실제 사람·회사 정보가 아니다).
 * DB 가 비어 있을 때(사용자가 한 명도 없을 때) 한 번만 넣는다. 다시 넣으려면 ./data/ 폴더를 지운다.
 *
 * 들어가는 것: 사용자 6명, 평가 항목, 채용 2개와 직무, 질문 은행, 신입 면접자 12명 + 경력 12명, 오늘 날짜의 면접.
 * - 신입(실시간 기능을 쓰지 않음): 사전 리포트, 준비된 질문과 예상 꼬리질문, 끝난 방, 면접관 평가. 전사·AI 평가·보고서는 없다.
 * - 경력(실시간 기능을 씀): 진행 중인 방, 전사, 꼬리질문 후보, AI 평가, 보고서, AI 작업 기록(관리자 집계 화면용).
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@Order(10)
@RequiredArgsConstructor
public class LocalSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalSeedRunner.class);
    private static final String MODEL = "seed-data";
    /** 시드 면접자의 유형. 순번대로 돌아가며 붙인다. */
    private static final ApplicantType[] NEW_GRAD_TYPES = { ApplicantType.OPEN, ApplicantType.SCHOLARSHIP, ApplicantType.OPEN,
            ApplicantType.INTERN };
    private static final ApplicantType[] EXPERIENCED_TYPES = { ApplicantType.CAREER, ApplicantType.PHD,
            ApplicantType.CAREER_MASTER, ApplicantType.CAREER };

    private final PlatformTransactionManager transactionManager;
    private final UserAccountRepository userRepository;
    private final EvalCriterionRepository criterionRepository;
    private final RecruitmentRepository recruitmentRepository;
    private final JobPostingRepository jobPostingRepository;
    private final QuestionBankRepository questionBankRepository;
    private final CandidateRepository candidateRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewAssigneeRepository assigneeRepository;
    private final BriefingRepository briefingRepository;
    private final QuestionRepository questionRepository;
    private final RoomRepository roomRepository;
    private final UtteranceRepository utteranceRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final InterviewerEvaluationRepository evaluationRepository;
    private final ReportRepository reportRepository;
    private final FollowUpRepository followUpRepository;
    private final AiJobRepository aiJobRepository;

    @Value("${app.seed.shift-dates-to-today:true}")
    private boolean shiftDatesToToday;

    @Override
    public void run(ApplicationArguments args) {
        new TransactionTemplate(transactionManager).execute(status -> {
            if (userRepository.count() == 0) {
                seed();
                log.info("시드 데이터를 넣었습니다. dev-login 사번: E1001(ADMIN), E1002~E1006(INTERVIEWER)");
            } else if (shiftDatesToToday) {
                shiftDates();
            }
            return null;
        });
    }

    /** 맨 처음 시드한 면접(오늘 09:30 으로 넣은 것)의 날짜를 기준으로, 모든 면접 일정을 오늘에 맞게 옮긴다. */
    private void shiftDates() {
        List<Interview> all = interviewRepository.findAll(Sort.by("id"));
        LocalDate anchor = null;
        for (Interview i : all) {
            if (i.getScheduledAt() != null) {
                anchor = i.getScheduledAt().toLocalDate();
                break;
            }
        }
        if (anchor == null) {
            return;
        }
        long days = ChronoUnit.DAYS.between(anchor, Times.today());
        if (days == 0) {
            return;
        }
        for (Interview i : all) {
            if (i.getScheduledAt() != null) {
                i.setScheduledAt(i.getScheduledAt().plusDays(days));
            }
        }
        // 방과 보고서의 시각도 같이 옮겨야 관리자 집계(진행 시간, 보고서 소요 시간)가 어긋나지 않는다.
        for (Room room : roomRepository.findAll()) {
            room.setCreatedAt(room.getCreatedAt().plusDays(days));
            if (room.getStartedAt() != null) {
                room.setStartedAt(room.getStartedAt().plusDays(days));
            }
            if (room.getEndedAt() != null) {
                room.setEndedAt(room.getEndedAt().plusDays(days));
            }
        }
        for (Report report : reportRepository.findAll()) {
            report.setGeneratedAt(report.getGeneratedAt().plusDays(days));
        }
        log.info("시드 면접 일정을 {}일 옮겼습니다(app.seed.shift-dates-to-today).", days);
    }

    private void seed() {
        // ---- 사용자 ----
        UserAccount kim = user("E1001", "김서연", "People팀", UserRole.ADMIN, "SW");
        UserAccount park = user("E1002", "박준호", "SW개발1팀", UserRole.INTERVIEWER, "SW", "AI");
        UserAccount lee = user("E1003", "이하린", "HW설계팀", UserRole.INTERVIEWER, "HW");
        UserAccount choi = user("E1004", "최민재", "AI연구소", UserRole.INTERVIEWER, "AI", "SW");
        UserAccount jung = user("E1005", "정유진", "경영지원팀", UserRole.INTERVIEWER, "MGMT");
        UserAccount han = user("E1006", "한지후", "플랫폼개발팀", UserRole.INTERVIEWER, "SW", "HW", "MGMT");

        seedCriteria();

        // ---- 채용과 직무 ----
        LocalDate today = Times.today();
        Recruitment newGrad = recruitment("2026 하반기 신입 공채", Track.NEW_GRAD, today.minusDays(45), today.plusDays(30));
        Recruitment experienced = recruitment("경력 상시 채용", Track.EXPERIENCED, today.minusDays(120), null);
        job(newGrad, "SW", "SW개발", "서비스 백엔드와 플랫폼을 개발합니다. 자료구조·운영체제·네트워크 기초와 한 가지 이상의 언어에 능숙해야 합니다. 협업과 코드 리뷰 경험을 우대합니다.");
        job(newGrad, "HW", "HW설계", "디지털·아날로그 회로와 보드를 설계하고 검증합니다. 회로이론, 신호 무결성, 계측 장비 사용 경험이 필요합니다.");
        job(newGrad, "AI", "AI연구", "기계학습 모델을 연구하고 제품에 적용합니다. 선형대수·확률통계 기초와 실험 설계 능력, 논문 구현 경험을 봅니다.");
        job(newGrad, "MGMT", "경영지원", "재무·인사·총무 업무를 지원합니다. 회계 기초와 엑셀 활용, 정확한 문서 작성과 소통 능력이 필요합니다.");
        job(experienced, "SW", "SW개발(경력)", "대용량 트래픽 서비스의 설계와 운영을 이끕니다. 5년 이상의 백엔드 경력, 장애 대응과 성능 개선 경험, 기술 리딩 경험을 봅니다.");
        job(experienced, "AI", "AI연구(경력)", "모델 서빙과 MLOps 를 포함한 AI 제품화를 이끕니다. 3년 이상의 관련 경력과 운영 환경에서의 모델 개선 사례가 필요합니다.");
        job(experienced, "HW", "HW설계(경력)", "양산 제품의 회로 설계와 검증을 주도합니다. 양산 이슈 해결 경험과 협력사 대응 경험을 봅니다.");

        seedQuestionBank();

        // ---- 신입 면접자 12명 ----
        // 신입 면접은 실시간 기능을 쓰지 않는다(app.live.new-grad-enabled 의 기본값). 그래서 준비된 질문·예상 꼬리질문·면접관 평가만 넣는다.
        // 끝난 면접의 방은 ENDED 지만 전사·꼬리질문 후보·AI 평가·보고서가 없고, 진행 중(LIVE)인 방도 두지 않는다.
        String[] names = { "이도윤", "김하은", "박시우", "최지아", "정예준", "강수아", "조건우", "윤다은", "장현우", "임채원", "한지호", "오유나" };
        String[][] jobs = { { "SW", "SW개발" }, { "HW", "HW설계" }, { "AI", "AI연구" }, { "MGMT", "경영지원" } };
        List<Candidate> grads = new ArrayList<Candidate>();
        for (int n = 0; n < names.length; n++) {
            String[] j = jobs[n % jobs.length];
            grads.add(candidate(newGrad, String.format("2026H-9%03d", n + 1), names[n], j[0], j[1], n, false));
        }
        LocalDateTime day = today.atStartOfDay();

        // 1번: 직무 완료(두 면접관 모두 평가 제출), 인성 예정
        Interview g1job = interview(grads.get(0), InterviewType.JOB, 1, InterviewStatus.DONE, day.plusHours(9).plusMinutes(30));
        assign(g1job, kim, park);
        endedWithoutLive(g1job, grads.get(0), kim, park);
        Interview g1ps = interview(grads.get(0), InterviewType.PERSONALITY, 2, InterviewStatus.SCHEDULED, day.plusHours(15));
        assign(g1ps, han, choi);
        briefing(g1ps, grads.get(0));

        // 2번: 직무 완료, 인성 예정(사전 리포트는 아직 없음)
        Interview g2job = interview(grads.get(1), InterviewType.JOB, 1, InterviewStatus.DONE, day.plusHours(10));
        assign(g2job, lee, han);
        endedWithoutLive(g2job, grads.get(1), lee, han);
        Interview g2ps = interview(grads.get(1), InterviewType.PERSONALITY, 2, InterviewStatus.SCHEDULED, day.plusHours(15).plusMinutes(30));
        assign(g2ps, lee, han);

        // 3번: 직무 평가 중(방은 끝났고 면접관 한 명만 평가를 제출함)
        Interview g3job = interview(grads.get(2), InterviewType.JOB, 1, InterviewStatus.EVALUATING, day.plusHours(10).plusMinutes(30));
        assign(g3job, park, choi);
        endedWithoutLive(g3job, grads.get(2), park);

        // 4~9번: 직무 예정(사전 리포트 완료). 4번은 방까지 만들어 둠
        UserAccount[][] pairs = { { jung, han }, { kim, park }, { lee, han }, { choi, park }, { jung, han }, { kim, choi } };
        for (int n = 3; n <= 8; n++) {
            Interview first = interview(grads.get(n), InterviewType.JOB, 1, InterviewStatus.SCHEDULED,
                    day.plusHours(13).plusMinutes((n - 3) * 40L));
            assign(first, pairs[n - 3][0], pairs[n - 3][1]);
            briefing(first, grads.get(n));
            if (n == 3) {
                room(first, RoomStatus.CREATED);
            }
        }
        // 10번: 내일 직무 예정(사전 리포트 없음), 11번: 일정 전(WAITING), 12번: 면접 없음(자동 매칭 대상)
        Interview g10 = interview(grads.get(9), InterviewType.JOB, 1, InterviewStatus.SCHEDULED, day.plusDays(1).plusHours(10));
        assign(g10, lee, han);
        Interview g11 = interview(grads.get(10), InterviewType.JOB, 1, InterviewStatus.WAITING, null);
        assign(g11, choi, park);

        // ---- 경력 면접자 12명 ----
        // 실시간 기능을 쓰는 쪽이다. 진행 중인 방, 전사, 꼬리질문 후보, AI 평가, 보고서와 관리자 집계 화면의 볼거리를 여기에 둔다.
        Candidate e1 = candidate(experienced, "EXP-2026-0001", "서지훈", "SW", "SW개발(경력)", 0, true);
        Candidate e2 = candidate(experienced, "EXP-2026-0002", "문가윤", "AI", "AI연구(경력)", 1, true);
        Candidate e3 = candidate(experienced, "EXP-2026-0003", "배성민", "HW", "HW설계(경력)", 2, true);
        Candidate e4 = candidate(experienced, "EXP-2026-0004", "신예린", "SW", "SW개발(경력)", 3, true);
        Candidate e5 = candidate(experienced, "EXP-2026-0005", "남도현", "AI", "AI연구(경력)", 4, true);

        // 경력 1번: 직무(1차) DONE → 인성(2차) SCHEDULED (진행 표시용. 인성의 사전 리포트에 1차 보고서의 "다음 차수에서 확인할 점"이 보인다)
        Interview e1job = interview(e1, InterviewType.JOB, 1, InterviewStatus.DONE, day.minusDays(1).plusHours(14));
        assign(e1job, park, han);
        completed(e1job, e1, true, park, han);
        Interview e1ps = interview(e1, InterviewType.PERSONALITY, 2, InterviewStatus.SCHEDULED, day.plusHours(16));
        assign(e1ps, park, choi);
        briefing(e1ps, e1);

        Interview e2job = interview(e2, InterviewType.JOB, 1, InterviewStatus.SCHEDULED, day.plusHours(14));
        assign(e2job, choi, park);
        briefing(e2job, e2);

        // 경력 3번: 직무 평가 중(방은 끝났고 보고서 초안까지 있음, 면접관 평가는 아직)
        Interview e3job = interview(e3, InterviewType.JOB, 1, InterviewStatus.EVALUATING, day.plusHours(11).plusMinutes(30));
        assign(e3job, lee, han);
        List<Question> e3q = briefing(e3job, e3);
        Room e3room = room(e3job, RoomStatus.ENDED);
        transcript(e3room, e3q, 4);
        aiEvaluation(e3job);
        report(e3job, 1, ReportBasis.LIVE, e3q);

        Interview e4job = interview(e4, InterviewType.JOB, 1, InterviewStatus.WAITING, null);
        assign(e4job, kim, park);

        // 경력 5번: 직무 진행 중(방 LIVE, 전사 일부).
        // 운영 현황판에서 "예정 시간을 넘김"으로 보이게, 지금 기준 80분 전 예정 · 78분 전 시작(예정 길이 60분)으로 둔다.
        Interview e5job = interview(e5, InterviewType.JOB, 1, InterviewStatus.IN_PROGRESS, Times.now().minusMinutes(80));
        assign(e5job, choi, park);
        List<Question> e5q = briefing(e5job, e5);
        Room e5room = room(e5job, RoomStatus.LIVE);
        e5room.setStartedAt(Times.now().minusMinutes(78));
        transcript(e5room, e5q, 2);

        seedAdminStatsDemo(experienced, day, new UserAccount[][] { { park, choi }, { lee, han }, { choi, park }, { kim, han },
                { kim, park }, { lee, han }, { park, han } });
    }

    /** 신입의 끝난 면접: 준비된 질문(앞의 넷은 물어봄), 끝난 방, 면접관 평가. 전사·AI 평가·보고서는 없다. */
    private void endedWithoutLive(Interview i, Candidate c, UserAccount... submitted) {
        List<Question> questions = briefing(i, c);
        for (int n = 0; n < 4 && n < questions.size(); n++) {
            questions.get(n).setAsked(true);
        }
        room(i, RoomStatus.ENDED);
        for (UserAccount u : submitted) {
            evaluation(i, u);
        }
    }

    /**
     * 관리자 집계 화면(운영 현황판, AI 품질, 면접관 편차)이 비어 보이지 않게 하는 추가 데이터.
     * 경력 채용에 끝난 면접 6건(DONE 4, EVALUATING 2)과 늦게 시작한 진행 중 면접 1건을 더한다.
     */
    private void seedAdminStatsDemo(Recruitment experienced, LocalDateTime day, UserAccount[][] pairs) {
        String[] names = { "권민서", "황준우", "안서현", "송지민", "류하람", "홍예서", "백도현" };
        String[][] jobs = { { "SW", "SW개발(경력)" }, { "AI", "AI연구(경력)" }, { "HW", "HW설계(경력)" } };
        LocalDateTime now = Times.now();
        for (int n = 0; n < names.length; n++) {
            String[] j = jobs[n % jobs.length];
            Candidate c = candidate(experienced, String.format("EXP-2026-%04d", 6 + n), names[n], j[0], j[1], 5 + n, true);
            if (n == 6) {
                // 예정보다 12분 늦게 시작한 진행 중 면접(전사는 방금까지 들어옴)
                Interview late = interview(c, InterviewType.JOB, 1, InterviewStatus.IN_PROGRESS, now.minusMinutes(20));
                assign(late, pairs[n][0], pairs[n][1]);
                Room lateRoom = room(late, RoomStatus.LIVE);
                lateRoom.setStartedAt(now.minusMinutes(8));
                for (Utterance u : transcript(lateRoom, briefing(late, c), 2)) {
                    u.setSavedAt(now.minusMinutes(1));
                }
                continue;
            }
            boolean done = n < 4;
            Interview first = interview(c, InterviewType.JOB, 1, done ? InterviewStatus.DONE : InterviewStatus.EVALUATING,
                    day.plusHours(8).plusMinutes(20L * n));
            assign(first, pairs[n][0], pairs[n][1]);
            List<Question> questions = briefing(first, c);
            Room room = room(first, RoomStatus.ENDED);
            List<Utterance> utterances = transcript(room, questions, 5);
            // 화자 판정 방법을 섞는다: 규칙 판정, 사람이 고친 것, 정하지 못한 것
            utterances.get(1).setSpeakerMethod(SpeakerMethod.RULE);
            utterances.get(3).setSpeakerMethod(SpeakerMethod.RULE);
            if (n % 2 == 0) {
                utterances.get(5).setSpeakerMethod(SpeakerMethod.MANUAL);
            }
            if (n == 3) {
                utterances.get(7).setSpeaker(Speaker.UNKNOWN);
                utterances.get(7).setSpeakerMethod(SpeakerMethod.NONE);
            }
            followUps(room, first, 4 + n % 3, n % 3);

            AiEvaluation ai = aiEvaluation(first);
            if (n == 1) {
                // AI 가 근거를 찾지 못해 점수를 내지 않은 항목
                ((ObjectNode) ai.getScores().get(3)).putNull("score");
            }
            // 방이 끝난 직후 AI 평가(35~50초) → 이어서 보고서(60~100초)
            LocalDateTime evaluationDone = aiJob(first, JobType.AI_EVALUATION, JobStatus.SUCCEEDED, room.getEndedAt(), 35 + n * 3);
            evaluation(first, pairs[n][0]);
            if (done) {
                evaluation(first, pairs[n][1]); // EVALUATING 인 면접은 한 명이 아직 제출하지 않았다.
            }
            if (n == 5) {
                aiJob(first, JobType.REPORT, JobStatus.FAILED, evaluationDone, 120); // 보고서 작업 실패 → 현황판의 JOB_FAILED
                continue;
            }
            report(first, 1, ReportBasis.LIVE, questions);
            aiJob(first, JobType.REPORT, JobStatus.SUCCEEDED, evaluationDone, 60 + n * 8);
            if (n % 2 == 0) {
                room.setPostSttModel("seed-stt");
                report(first, 2, ReportBasis.REFINED, questions);
                aiJob(first, JobType.REPORT, JobStatus.SUCCEEDED, room.getEndedAt().plusMinutes(3), 75 + n * 5);
            }
        }
    }

    /** 꼬리질문 후보 count 개(그 가운데 used 개는 채택됨). */
    private void followUps(Room room, Interview i, int count, int used) {
        String[] texts = { "그때 가장 어려웠던 점은 무엇이었습니까?", "측정 결과가 예상과 달랐던 적이 있습니까?", "다시 한다면 무엇을 다르게 하겠습니까?",
                "그 결정에 반대한 팀원은 어떻게 설득했습니까?", "그 경험이 지원 직무와 어떻게 이어집니까?", "결과를 어떻게 검증했습니까?" };
        for (int n = 0; n < count && n < texts.length; n++) {
            FollowUp f = new FollowUp();
            f.setRoomId(room.getId());
            f.setBasedOnSeq(2 + n);
            f.setClientId("f-" + (2 + n) + "-1");
            f.setText(texts[n]);
            f.setRationale("답변을 구체화하기 위한 질문");
            f.setCreatedAt(Times.now());
            if (n < used) {
                Question q = question(i, 10 + n, QuestionSource.FOLLOW_UP, "꼬리질문", texts[n], "답변을 구체화하기 위한 질문");
                q.setAsked(true);
                f.setUsed(true);
                f.setQuestionId(q.getId());
            }
            followUpRepository.save(f);
        }
    }

    /**
     * 끝난 AI 작업 한 건. 접수(createdAt) → 2초 기다린 뒤 시작 → runSeconds 초 뒤 끝난 것으로 넣는다
     * (시스템 상태의 "기다리는 시간"이 0초로 보이지 않게). 끝난 시각을 돌려준다.
     */
    private LocalDateTime aiJob(Interview i, JobType type, JobStatus status, LocalDateTime createdAt, int runSeconds) {
        AiJob job = new AiJob();
        job.setType(type);
        job.setStatus(status);
        job.setInterviewId(i.getId());
        job.setWorkerId("seed");
        job.setCreatedAt(createdAt);
        job.setStartedAt(createdAt.plusSeconds(2));
        job.setFinishedAt(createdAt.plusSeconds(2L + runSeconds));
        if (status == JobStatus.FAILED) {
            job.setErrorCode("MODEL_FAILED");
            job.setErrorMessage("모델이 응답하지 않았습니다. (시드 데이터)");
        }
        aiJobRepository.save(job);
        return job.getFinishedAt();
    }

    // ------------------------------------------------------------------ 기준 데이터

    private UserAccount user(String employeeNo, String name, String department, UserRole role, String... jobCodes) {
        UserAccount u = new UserAccount();
        u.setEmployeeNo(employeeNo);
        u.setName(name);
        u.setDepartment(department);
        u.setRole(role);
        u.setJobCodes(new ArrayList<String>(Arrays.asList(jobCodes)));
        return userRepository.save(u);
    }

    private void seedCriteria() {
        String[][] newGradJob = { { "PROBLEM_SOLVING", "문제 해결", "문제를 정의하고 해결 과정을 논리적으로 설명하는가" },
                { "LOGIC", "논리성", "주장과 근거가 일관되고 구조적인가" }, { "JOB_KNOWLEDGE", "직무 지식", "직무에 필요한 기초 지식을 갖췄는가" },
                { "COMMUNICATION", "전달력", "핵심을 간결하고 이해하기 쉽게 전달하는가" } };
        String[][] personality = { { "TEAMWORK", "협업", "다른 사람과 함께 일한 경험과 태도" }, { "INTEGRITY", "성실·정직", "책임감 있게 일하고 사실대로 말하는가" },
                { "GROWTH", "성장 가능성", "배우려는 태도와 피드백 수용" }, { "COMMUNICATION", "소통", "상대의 말을 듣고 분명하게 답하는가" } };
        String[][] experiencedJob = { { "JOB_KNOWLEDGE", "직무 지식", "직무의 핵심 개념과 원리를 이해하는가" }, { "PROBLEM_SOLVING", "문제 해결", "실무 문제를 풀어 가는 방식" },
                { "EXPERTISE", "전문성", "경험에서 나온 판단과 깊이" }, { "COLLABORATION", "협업", "다른 직군·팀과 일하는 방식" } };
        criteria(Track.NEW_GRAD, InterviewType.JOB, newGradJob);
        criteria(Track.EXPERIENCED, InterviewType.JOB, experiencedJob);
        for (Track track : Track.values()) {
            criteria(track, InterviewType.PERSONALITY, personality);
        }
    }

    private void criteria(Track track, InterviewType type, String[][] rows) {
        int order = 0;
        for (String[] row : rows) {
            EvalCriterion c = new EvalCriterion();
            c.setTrack(track);
            c.setInterviewType(type);
            c.setCode(row[0]);
            c.setName(row[1]);
            c.setDescription(row[2]);
            c.setMaxScore(5);
            c.setOrderNo(++order);
            criterionRepository.save(c);
        }
    }

    private Recruitment recruitment(String title, Track track, LocalDate start, LocalDate end) {
        Recruitment r = new Recruitment();
        r.setTitle(title);
        r.setTrack(track);
        r.setStartDate(start);
        r.setEndDate(end);
        r.setCreatedAt(Times.now());
        return recruitmentRepository.save(r);
    }

    private void job(Recruitment r, String code, String title, String jd) {
        JobPosting j = new JobPosting();
        j.setRecruitmentId(r.getId());
        j.setJobCode(code);
        j.setTitle(title);
        j.setJdText(jd);
        j.setUpdatedAt(Times.now());
        jobPostingRepository.save(j);
    }

    private void seedQuestionBank() {
        bank("SW", InterviewType.JOB, "CS기초", "프로세스와 스레드의 차이를 설명하고, 스레드를 쓸 때 주의할 점을 말해 주세요.");
        bank("SW", InterviewType.JOB, "CS기초", "해시 테이블의 충돌을 처리하는 방법을 비교해 주세요.");
        bank("SW", InterviewType.JOB, "설계", "트래픽이 10배로 늘면 지금 설계에서 어디가 먼저 문제가 됩니까?");
        bank("SW", InterviewType.JOB, "데이터베이스", "인덱스가 오히려 성능을 떨어뜨리는 경우를 설명해 주세요.");
        bank("SW", null, "협업", "코드 리뷰에서 의견이 갈렸을 때 어떻게 정리했습니까?");
        bank("SW", InterviewType.JOB, "장애 대응", "가장 기억에 남는 장애와 그 뒤에 바꾼 것을 말해 주세요.");
        bank("HW", InterviewType.JOB, "회로", "풀업 저항의 값을 정할 때 무엇을 고려합니까?");
        bank("HW", InterviewType.JOB, "회로", "신호 반사가 생기는 이유와 줄이는 방법을 설명해 주세요.");
        bank("HW", InterviewType.JOB, "검증", "오실로스코프로 타이밍 문제를 찾았던 경험을 말해 주세요.");
        bank("HW", InterviewType.JOB, "설계", "전원 설계에서 디커플링 커패시터를 배치하는 기준은 무엇입니까?");
        bank("HW", null, "양산", "시제품과 양산품의 차이 때문에 생긴 문제를 겪은 적이 있습니까?");
        bank("HW", InterviewType.JOB, "설계", "가장 어려웠던 보드 설계와 그 이유를 말해 주세요.");
        bank("AI", InterviewType.JOB, "기계학습", "과적합을 확인하는 방법과 줄이는 방법을 설명해 주세요.");
        bank("AI", InterviewType.JOB, "기계학습", "정밀도와 재현율 가운데 무엇을 우선할지 어떻게 정합니까?");
        bank("AI", InterviewType.JOB, "실험", "실험 결과가 재현되지 않을 때 무엇부터 확인합니까?");
        bank("AI", InterviewType.JOB, "서빙", "모델 응답 시간을 줄였던 경험을 말해 주세요.");
        bank("AI", null, "데이터", "학습 데이터의 품질 문제를 어떻게 찾았습니까?");
        bank("AI", InterviewType.JOB, "제품화", "연구 결과를 제품에 넣으면서 포기한 것은 무엇입니까?");
        bank("MGMT", InterviewType.JOB, "회계", "발생주의와 현금주의의 차이를 예를 들어 설명해 주세요.");
        bank("MGMT", InterviewType.JOB, "분석", "비용이 갑자기 늘었을 때 원인을 찾는 순서를 말해 주세요.");
        bank("MGMT", InterviewType.JOB, "문서", "보고서를 쓸 때 가장 먼저 정하는 것은 무엇입니까?");
        bank("MGMT", InterviewType.JOB, "규정", "규정과 현장의 요구가 부딪혔을 때 어떻게 처리했습니까?");
        bank("MGMT", null, "소통", "다른 부서의 협조를 얻기 어려웠던 경험을 말해 주세요.");
        bank(null, InterviewType.PERSONALITY, "협업", "팀에서 의견이 충돌했을 때 본인은 어떤 역할을 했습니까?");
        bank(null, InterviewType.PERSONALITY, "협업", "함께 일하기 어려웠던 사람과 어떻게 일했습니까?");
        bank(null, InterviewType.PERSONALITY, "책임감", "맡은 일을 기한 안에 못 끝낼 것 같았을 때 어떻게 했습니까?");
        bank(null, InterviewType.PERSONALITY, "성장", "최근 1년 동안 새로 배운 것과 배운 방법을 말해 주세요.");
        bank(null, InterviewType.PERSONALITY, "성장", "받았던 피드백 가운데 가장 받아들이기 어려웠던 것은 무엇입니까?");
        bank(null, InterviewType.PERSONALITY, "가치관", "일할 때 절대 타협하지 않는 원칙이 있습니까?");
        bank(null, null, "지원동기", "여러 회사 가운데 우리 회사를 고른 이유는 무엇입니까?");
        bank(null, null, "마무리", "마지막으로 하고 싶은 말이나 질문이 있습니까?");
    }

    private void bank(String jobCode, InterviewType type, String category, String text) {
        QuestionBankItem item = new QuestionBankItem();
        item.setJobCode(jobCode);
        item.setInterviewType(type);
        item.setCategory(category);
        item.setText(text);
        item.setCreatedAt(Times.now());
        questionBankRepository.save(item);
    }

    // ------------------------------------------------------------------ 면접자와 면접

    private Candidate candidate(Recruitment r, String applicantNo, String name, String jobCode, String jobTitle, int n,
            boolean experienced) {
        String[] schools = { "한빛대학교", "새솔대학교", "가람대학교", "누리대학교" };
        CandidateProfile p = new CandidateProfile();
        String major = "SW".equals(jobCode) ? "컴퓨터공학" : "HW".equals(jobCode) ? "전자공학" : "AI".equals(jobCode) ? "인공지능학" : "경영학";
        p.getEducation().add(new CandidateProfile.Education(schools[n % schools.length], major, experienced ? "석사" : "학사",
                String.format("%.1f/4.5", 3.4 + (n % 8) * 0.1)));
        p.getCourses().addAll("SW".equals(jobCode) ? Arrays.asList("자료구조", "운영체제", "데이터베이스")
                : "HW".equals(jobCode) ? Arrays.asList("회로이론", "디지털논리회로", "신호및시스템")
                        : "AI".equals(jobCode) ? Arrays.asList("기계학습", "선형대수", "확률통계") : Arrays.asList("회계원리", "재무관리", "조직행동론"));
        p.getEssays().add(new CandidateProfile.TitledText("지원동기", jobTitle + " 직무에서 오래 일하고 싶어 지원했습니다. 전공 수업과 프로젝트에서 이 일이 저와 잘 맞는다는 것을 확인했습니다."));
        p.getEssays().add(new CandidateProfile.TitledText("직무 역량과 경험", "문제를 작게 나눠 원인을 찾는 데 자신이 있습니다. 팀 프로젝트에서 성능 문제의 원인을 측정으로 찾아 해결했습니다."));
        p.getEssays().add(new CandidateProfile.TitledText("입사 후 포부", "3년 안에 담당 영역을 혼자 책임질 수 있는 수준이 되겠습니다."));
        p.getProjects().add(new CandidateProfile.TitledText("캡스톤 프로젝트", "4인 팀에서 핵심 기능을 맡아 설계부터 시연까지 진행했고, 처리 시간을 40% 줄였습니다."));
        if (experienced) {
            p.getCareers().add(new CandidateProfile.Career("가상테크", "선임 엔지니어", "2020.03~2024.12", "핵심 서비스의 설계와 운영을 맡아 장애 건수를 절반으로 줄였습니다."));
            p.getCareers().add(new CandidateProfile.Career("샘플소프트", "엔지니어", "2017.01~2020.02", "신규 제품의 초기 개발에 참여했습니다."));
        }
        p.getCertificates().add("MGMT".equals(jobCode) ? "전산회계 1급" : "정보처리기사");
        p.getExtra().put("languages", "TOEIC " + (800 + (n % 10) * 15));

        Candidate c = new Candidate();
        c.setRecruitmentId(r.getId());
        c.setApplicantNo(applicantNo);
        c.setName(name);
        c.setTrack(r.getTrack());
        c.setJobCode(jobCode);
        c.setJobTitle(jobTitle);
        c.setApplicantType(experienced ? EXPERIENCED_TYPES[n % EXPERIENCED_TYPES.length] : NEW_GRAD_TYPES[n % NEW_GRAD_TYPES.length]);
        c.setProfile(p);
        // 원본에는 있었지만 저장하지 않은 항목(값은 어디에도 없다)
        c.setBlindedFields(new ArrayList<String>(Arrays.asList("gender", "birthDate", "family")));
        c.setSourceType("SEED");
        c.setCreatedAt(Times.now());
        c.setUpdatedAt(Times.now());
        return candidateRepository.save(c);
    }

    private Interview interview(Candidate c, InterviewType type, int seq, InterviewStatus status, LocalDateTime at) {
        Interview i = InterviewService.newInterview(c, type, seq);
        i.setStatus(status);
        i.setScheduledAt(at);
        return interviewRepository.save(i);
    }

    private void assign(Interview i, UserAccount... users) {
        for (UserAccount u : users) {
            assigneeRepository.save(new InterviewAssignee(i.getId(), u.getId(), MatchedBy.JD));
        }
    }

    /** 끝난 면접 한 건을 통째로 채운다: 브리핑, 방, 전사, AI 평가, 면접관 평가, 보고서. */
    private void completed(Interview i, Candidate c, boolean refined, UserAccount... interviewers) {
        List<Question> questions = briefing(i, c);
        Room room = room(i, RoomStatus.ENDED);
        if (refined) {
            room.setPostSttModel("seed-stt");
        }
        List<Utterance> utterances = transcript(room, questions, 4);
        if (refined) {
            for (Utterance u : utterances) {
                u.setSource(TranscriptSource.POST);
            }
        }
        aiEvaluation(i);
        for (UserAccount u : interviewers) {
            evaluation(i, u);
        }
        report(i, 1, ReportBasis.LIVE, questions);
        if (refined) {
            report(i, 2, ReportBasis.REFINED, questions);
        }
    }

    private List<Question> briefing(Interview i, Candidate c) {
        ObjectNode summary = Json.object();
        String major = c.getProfile().getEducation().get(0).getMajor();
        summary.put("headline", major + " 전공, 프로젝트 경험이 뚜렷한 " + c.getJobTitle() + " 지원자");
        summary.putArray("keyPoints").add("캡스톤 프로젝트에서 핵심 기능을 맡아 처리 시간을 40% 줄임").add("전공 기초 과목을 고르게 이수함")
                .add("자격증: " + c.getProfile().getCertificates().get(0));
        summary.putArray("strengths").add("문제의 원인을 측정으로 찾는 습관").add("결과를 수치로 설명함");
        summary.putArray("checkPoints").add("팀 프로젝트에서 본인이 직접 한 일의 범위").add("40% 개선의 측정 방법");
        ArrayNode timeline = summary.putArray("timeline");
        for (CandidateProfile.Career career : c.getProfile().getCareers()) {
            timeline.addObject().put("period", career.getPeriod()).put("text", career.getCompany() + " " + career.getRole());
        }
        timeline.addObject().put("period", "최근").put("text", "캡스톤 프로젝트");

        Briefing b = new Briefing();
        b.setInterviewId(i.getId());
        b.setStatus(JobStatus.SUCCEEDED);
        b.setGeneratedAt(Times.now());
        b.setModel(MODEL);
        b.setSummary(summary);
        b.setExcluded(new ArrayList<String>(c.getBlindedFields()));
        briefingRepository.save(b);
        // 이 사전 리포트를 만든 작업 기록(어제 접수돼 25~40초 걸린 것으로 둔다).
        aiJob(i, JobType.BRIEFING, JobStatus.SUCCEEDED, Times.now().minusDays(1).plusMinutes(i.getId() % 60), 25 + (int) (i.getId() % 4) * 5);

        List<Question> questions = new ArrayList<Question>();
        questions.add(question(i, 1, QuestionSource.JD, "직무", c.getJobTitle() + " 직무에서 가장 중요하다고 생각하는 역량은 무엇입니까?", "직무 이해도 확인"));
        questions.add(question(i, 2, QuestionSource.RESUME, "프로젝트", "캡스톤 프로젝트에서 맡은 역할은 무엇이었습니까?", "기여도 확인"));
        questions.add(question(i, 3, QuestionSource.RESUME, "프로젝트", "처리 시간을 40% 줄였다고 했는데, 무엇을 어떻게 측정했습니까?", "성과의 사실 확인"));
        questions.add(question(i, 4, QuestionSource.RESUME, "자기소개서", "문제를 작게 나눠 원인을 찾았던 사례를 하나 설명해 주세요.", "문제 해결 방식 확인"));
        questions.add(question(i, 5, QuestionSource.JD, "협업", "팀원과 의견이 달랐을 때 어떻게 결정했습니까?", "협업 방식 확인"));
        questions.add(question(i, 6, QuestionSource.BANK, "마무리", "마지막으로 하고 싶은 말이나 질문이 있습니까?", "질문 은행에서 고름"));
        if (i.getTrack() == Track.NEW_GRAD) {
            // 신입 면접은 실시간 기능을 쓰지 않으므로(app.live.new-grad-enabled) 예상 꼬리질문을 미리 넣어 둔다.
            predictedFollowUp(i, 7, questions.get(1), "그 역할에서 혼자 결정한 것과 팀이 함께 결정한 것을 나눠 말해 주세요.");
            predictedFollowUp(i, 8, questions.get(1), "맡은 부분에서 가장 오래 걸린 일은 무엇이었습니까?");
            predictedFollowUp(i, 9, questions.get(2), "측정할 때 조건을 어떻게 같게 맞췄습니까?");
            predictedFollowUp(i, 10, questions.get(2), "40%가 아닌 다른 결과가 나온 측정도 있었습니까?");
        }
        return questions;
    }

    private Question question(Interview i, int orderNo, QuestionSource source, String category, String text, String intent) {
        Question q = new Question();
        q.setInterviewId(i.getId());
        q.setSource(source);
        q.setCategory(category);
        q.setText(text);
        q.setIntent(intent);
        q.setOrderNo(orderNo);
        q.setCreatedAt(Times.now());
        return questionRepository.save(q);
    }

    private void predictedFollowUp(Interview i, int orderNo, Question parent, String text) {
        Question q = question(i, orderNo, QuestionSource.FOLLOW_UP, QuestionService.PREDICTED_FOLLOW_UP_CATEGORY, text, null);
        q.setParentQuestionId(parent.getId());
    }

    private Room room(Interview i, RoomStatus status) {
        Room room = new Room();
        room.setInterviewId(i.getId());
        room.setStatus(status);
        LocalDateTime base = i.getScheduledAt() == null ? Times.now() : i.getScheduledAt();
        room.setCreatedAt(base.minusMinutes(5));
        if (status != RoomStatus.CREATED) {
            room.setStartedAt(base);
        }
        if (status == RoomStatus.ENDED) {
            room.setEndedAt(base.plusMinutes(i.getDurationMinutes() == null ? 30 : i.getDurationMinutes()));
        }
        return roomRepository.save(room);
    }

    /** 질문-답변 쌍을 전사로 넣는다. 질문에는 "물어봄" 표시를 한다. */
    private List<Utterance> transcript(Room room, List<Question> questions, int pairs) {
        String[] answers = { "네, 저는 기초를 정확히 아는 것이 가장 중요하다고 생각합니다. 그래서 전공 과목을 다시 정리하면서 직접 구현해 보는 방식으로 공부했습니다.",
                "저는 핵심 기능의 설계와 구현을 맡았습니다. 일정 관리는 다른 팀원이 했고, 저는 성능 문제가 생긴 부분을 끝까지 책임졌습니다.",
                "요청 한 건을 처리하는 평균 시간을 기준으로 쟀습니다. 같은 입력 1만 건으로 바꾸기 전과 후를 세 번씩 재서 평균을 비교했습니다.",
                "처음에는 전체가 느리다고만 알았는데, 구간별로 시간을 찍어 보니 한 단계에서 대부분의 시간이 쓰이고 있었습니다. 그 부분의 자료구조를 바꿔 해결했습니다.",
                "각자 근거를 문서로 정리해 비교했습니다. 측정할 수 있는 것은 실험으로 정했고, 그렇지 않은 것은 되돌리기 쉬운 쪽을 골랐습니다." };
        List<Utterance> result = new ArrayList<Utterance>();
        int seq = 0;
        long ms = 5000;
        for (int n = 0; n < pairs && n < questions.size() && n < answers.length; n++) {
            Question q = questions.get(n);
            int questionSeq = ++seq;
            result.add(utterance(room, questionSeq, Speaker.INTERVIEWER, SpeakerMethod.QUESTION_MATCH, q.getText(), q.getText(), ms, ms + 6000, q.getId()));
            q.setAsked(true);
            q.setAskedAtSeq(questionSeq);
            ms += 8000;
            result.add(utterance(room, ++seq, Speaker.CANDIDATE, SpeakerMethod.LLM, "어 " + answers[n], answers[n], ms, ms + 25000, null));
            ms += 30000;
        }
        return result;
    }

    private Utterance utterance(Room room, int seq, Speaker speaker, SpeakerMethod method, String raw, String refined,
            long startMs, long endMs, Long matchedQuestionId) {
        Utterance u = new Utterance();
        u.setRoomId(room.getId());
        u.setSeq(seq);
        u.setSpeaker(speaker);
        u.setSpeakerMethod(method);
        u.setRawText(raw);
        u.setRefinedText(refined);
        u.setStartMs(startMs);
        u.setEndMs(endMs);
        u.setMatchedQuestionId(matchedQuestionId);
        u.setSource(TranscriptSource.LIVE);
        if (room.getStartedAt() != null) {
            u.setSavedAt(room.getStartedAt().plusSeconds(endMs / 1000));
        }
        return utteranceRepository.save(u);
    }

    private AiEvaluation aiEvaluation(Interview i) {
        ArrayNode scores = Json.array();
        int n = 0;
        for (EvalCriterion c : criterionRepository.findByTrackAndInterviewTypeOrderByOrderNoAsc(i.getTrack(), i.getType())) {
            ObjectNode node = scores.addObject();
            node.put("criterionCode", c.getCode());
            node.put("criterionName", c.getName());
            node.put("score", 3 + (n++ % 2));
            node.put("maxScore", c.getMaxScore());
            node.put("rationale", c.getName() + ": 답변에서 구체적인 사례와 측정 방법을 설명했습니다. (참고용)");
            node.putArray("evidenceSeqs").add(2).add(4);
        }
        AiEvaluation e = new AiEvaluation();
        e.setInterviewId(i.getId());
        e.setModel(MODEL);
        e.setGeneratedAt(Times.now());
        e.setScores(scores);
        e.setOverallComment("질문 의도에 맞게 답했고 경험을 수치로 설명했습니다. 본인 기여 범위는 추가 확인이 필요합니다. (AI 참고 평가)");
        return aiEvaluationRepository.save(e);
    }

    /** 면접관 평가. 면접관마다 점수 성향이 조금 다르다(박준호는 후하고, 이하린·정유진은 짜다). */
    private void evaluation(Interview i, UserAccount u) {
        int bias = "E1002".equals(u.getEmployeeNo()) ? 1
                : ("E1003".equals(u.getEmployeeNo()) || "E1005".equals(u.getEmployeeNo())) ? -1 : 0;
        int[] base = { 4, 3, 4, 3 };
        ArrayNode scores = Json.array();
        int index = 0;
        int sum = 0;
        for (EvalCriterion c : criterionRepository.findByTrackAndInterviewTypeOrderByOrderNoAsc(i.getTrack(), i.getType())) {
            int score = base[index % base.length] + bias + ((i.getId() + index) % 3 == 0 ? 1 : 0);
            score = Math.max(1, Math.min(c.getMaxScore(), score));
            scores.addObject().put("criterionCode", c.getCode()).put("score", score).put("comment", "");
            sum += score;
            index++;
        }
        double mean = index == 0 ? 0 : (double) sum / index;
        InterviewerEvaluation e = new InterviewerEvaluation();
        e.setInterviewId(i.getId());
        e.setUserId(u.getId());
        e.setMemo("측정 방법을 구체적으로 설명함. 팀 내 역할은 한 번 더 확인하면 좋겠음.");
        e.setScores(scores);
        e.setGrade(mean >= 4.5 ? "A" : mean >= 3.5 ? "B+" : mean >= 2.5 ? "B" : "C");
        e.setSubmitted(true);
        e.setUpdatedAt(Times.now());
        e.setSubmittedAt(Times.now());
        evaluationRepository.save(e);
    }

    private void report(Interview i, int version, ReportBasis basis, List<Question> questions) {
        boolean detailed = i.getTrack() == Track.EXPERIENCED;
        ObjectNode content = Json.object();
        content.put("headline", "기초가 탄탄하고 경험을 수치로 설명하는 지원자");
        content.put("overallComment", "질문 의도를 정확히 파악해 답했습니다. 프로젝트 성과를 측정 방법과 함께 설명했습니다. "
                + "문제를 구간별로 나눠 원인을 찾는 방식이 일관됩니다. 다만 팀 안에서 본인이 직접 한 일의 범위는 추가 확인이 필요합니다."
                + (basis == ReportBasis.REFINED ? " (사후 STT 보정본 기준)" : " (실시간 전사 기준 초안)"));
        content.putArray("strengths").add("성과를 측정 방법과 함께 설명함").add("문제를 나눠 원인을 찾는 접근");
        content.putArray("concerns").add("팀 프로젝트에서 본인 기여 범위가 불분명한 부분이 있음");
        ArrayNode qa = content.putArray("qaHighlights");
        qa.addObject().put("question", questions.get(1).getText()).put("answerSummary", "핵심 기능의 설계와 구현을 맡았고 성능 문제를 책임졌다고 답함")
                .putArray("seqs").add(3).add(4);
        qa.addObject().put("question", questions.get(2).getText()).put("answerSummary", "같은 입력 1만 건으로 전후를 세 번씩 재서 비교했다고 답함")
                .putArray("seqs").add(5).add(6);
        ArrayNode competencies = content.putArray("competencies");
        ArrayNode careerChecks = content.putArray("careerChecks");
        ArrayNode nextRoundChecks = content.putArray("nextRoundChecks");
        ArrayNode risks = content.putArray("risks");
        if (detailed) {
            competencies.addObject().put("name", "전문성").put("assessment", "담당 영역의 설계 판단을 근거와 함께 설명함")
                    .putArray("evidence").add("성능 문제를 구간별 측정으로 좁혀 간 사례");
            competencies.addObject().put("name", "주도성").put("assessment", "문제를 스스로 찾아 끝까지 해결한 경험이 있음")
                    .putArray("evidence").add("장애 건수를 줄인 과정 설명");
            careerChecks.addObject().put("claim", "핵심 서비스의 설계와 운영을 맡아 장애 건수를 절반으로 줄였다").put("finding",
                    "면접에서 역할과 측정 기준을 구체적으로 설명해 대체로 확인됨");
            nextRoundChecks.add("기술 리딩 경험의 범위(인원, 기간)").add("이직 사유");
            risks.add("직전 회사에서의 역할이 이력서 표현보다 좁을 가능성");
        }
        Report r = new Report();
        r.setInterviewId(i.getId());
        r.setVersion(version);
        r.setBasis(basis);
        r.setDetail(ReportDetail.of(i.getTrack()));
        // 방이 끝난 뒤 초안은 30~70초, 보정본은 3~6분쯤 걸린 것으로 둔다(AI 품질 화면의 소요 시간).
        Room room = roomRepository.findByInterviewId(i.getId()).orElse(null);
        LocalDateTime generatedAt = Times.now();
        if (room != null && room.getEndedAt() != null) {
            generatedAt = basis == ReportBasis.REFINED ? room.getEndedAt().plusSeconds(180 + (i.getId() % 4) * 60)
                    : room.getEndedAt().plusSeconds(30 + (i.getId() % 5) * 10);
        }
        r.setGeneratedAt(generatedAt);
        r.setModel(MODEL);
        r.setContent(content);
        reportRepository.save(r);
    }
}
