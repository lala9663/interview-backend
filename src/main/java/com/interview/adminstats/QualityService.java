package com.interview.adminstats;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.common.Times;
import com.interview.evaluation.AiEvaluation;
import com.interview.evaluation.AiEvaluationRepository;
import com.interview.evaluation.EvalCriterion;
import com.interview.evaluation.EvalCriterionRepository;
import com.interview.evaluation.InterviewerEvaluation;
import com.interview.evaluation.InterviewerEvaluationRepository;
import com.interview.interview.Interview;
import com.interview.interview.InterviewRepository;
import com.interview.job.AiJobRepository;
import com.interview.job.JobStatus;
import com.interview.job.JobType;
import com.interview.question.QuestionRepository;
import com.interview.question.QuestionSource;
import com.interview.recruitment.RecruitmentService;
import com.interview.report.ReportBasis;
import com.interview.report.ReportRepository;
import com.interview.room.FollowUpRepository;
import com.interview.room.Room;
import com.interview.room.RoomRepository;
import com.interview.room.RoomStatus;
import com.interview.room.Speaker;
import com.interview.room.SpeakerMethod;
import com.interview.room.UtteranceRepository;

import lombok.RequiredArgsConstructor;

/**
 * AI 품질 점검(계약 2.11 의 /admin/quality). 읽기 전용 집계다.
 *
 * 건수를 세는 것(전사, 꼬리질문, 질문, 작업, 보고서 시각)은 DB 에서 JPQL 집계로 가져온다.
 * 점수 비교만은 점수가 JSON(CLOB)에 들어 있어 평가 행을 읽어 자바에서 계산한다(면접 수 × 면접관 수 만큼의 행).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QualityService {

    /** "준비된 질문" = 브리핑이 만든 질문. 수동 추가·꼬리질문 채택은 뺀다. */
    private static final List<QuestionSource> PREPARED = Arrays.asList(QuestionSource.JD, QuestionSource.RESUME,
            QuestionSource.BANK);

    private final RecruitmentService recruitmentService;
    private final InterviewRepository interviewRepository;
    private final RoomRepository roomRepository;
    private final UtteranceRepository utteranceRepository;
    private final FollowUpRepository followUpRepository;
    private final QuestionRepository questionRepository;
    private final ReportRepository reportRepository;
    private final AiJobRepository aiJobRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final InterviewerEvaluationRepository evaluationRepository;
    private final EvalCriterionRepository criterionRepository;

    public QualityDtos.Response quality(Long recruitmentId) {
        recruitmentService.get(recruitmentId);
        QualityDtos.Response response = new QualityDtos.Response();
        response.setGeneratedAt(Times.toOffset(Times.now()));

        Map<Long, Interview> interviews = new HashMap<Long, Interview>();
        for (Interview i : interviewRepository.findByRecruitmentIdOrderByCandidateIdAscSeqAsc(recruitmentId)) {
            interviews.put(i.getId(), i);
        }
        List<Room> rooms = roomRepository.findByRecruitmentId(recruitmentId);
        long ended = 0;
        for (Room room : rooms) {
            if (room.getStatus() == RoomStatus.ENDED) {
                ended++;
            }
        }
        response.setEndedInterviewCount(ended);

        fillScores(response, recruitmentId, interviews);
        fillFollowUps(response.getFollowUps(), recruitmentId);
        fillSpeaker(response.getSpeaker(), recruitmentId);
        fillQuestions(response.getQuestions(), recruitmentId);
        fillReports(response.getReports(), recruitmentId, rooms);
        fillJobs(response.getJobs(), recruitmentId);
        return response;
    }

    // ------------------------------------------------------------------ 점수 차이와 AI 채점 범위

    private void fillScores(QualityDtos.Response response, Long recruitmentId, Map<Long, Interview> interviews) {
        // 평가 항목: (트랙, 유형) → 항목 목록, 코드 → 이름
        Map<String, List<EvalCriterion>> criteriaByKind = new HashMap<String, List<EvalCriterion>>();
        Map<String, String> criterionNames = new HashMap<String, String>();
        for (EvalCriterion c : criterionRepository.findAll()) {
            String key = c.getTrack() + "/" + c.getInterviewType();
            List<EvalCriterion> list = criteriaByKind.get(key);
            if (list == null) {
                list = new ArrayList<EvalCriterion>();
                criteriaByKind.put(key, list);
            }
            list.add(c);
            if (!criterionNames.containsKey(c.getCode())) {
                criterionNames.put(c.getCode(), c.getName());
            }
        }

        // 면접관 점수: 면접 → 항목 코드 → 제출된 평가들의 점수
        Map<Long, Map<String, List<Double>>> interviewerScores = new HashMap<Long, Map<String, List<Double>>>();
        for (InterviewerEvaluation e : evaluationRepository.findSubmittedByRecruitmentId(recruitmentId)) {
            Map<String, List<Double>> byCode = interviewerScores.get(e.getInterviewId());
            if (byCode == null) {
                byCode = new HashMap<String, List<Double>>();
                interviewerScores.put(e.getInterviewId(), byCode);
            }
            for (Map.Entry<String, Double> s : ScoreJson.scores(e.getScores()).entrySet()) {
                List<Double> list = byCode.get(s.getKey());
                if (list == null) {
                    list = new ArrayList<Double>();
                    byCode.put(s.getKey(), list);
                }
                list.add(s.getValue());
            }
        }

        long expected = 0;
        long scored = 0;
        List<Double> diffs = new ArrayList<Double>();
        Map<String, List<double[]>> pairsByCode = new TreeMap<String, List<double[]>>(); // 코드 → [ai, 면접관 평균]
        for (AiEvaluation ai : aiEvaluationRepository.findByRecruitmentId(recruitmentId)) {
            Interview interview = interviews.get(ai.getInterviewId());
            if (interview == null) {
                continue;
            }
            List<EvalCriterion> criteria = criteriaByKind.get(interview.getTrack() + "/" + interview.getType());
            expected += criteria == null ? 0 : criteria.size();
            Map<String, Double> aiScores = ScoreJson.scores(ai.getScores());
            scored += aiScores.size();

            Map<String, List<Double>> byCode = interviewerScores.get(ai.getInterviewId());
            if (byCode == null) {
                continue;
            }
            for (Map.Entry<String, Double> s : aiScores.entrySet()) {
                List<Double> humans = byCode.get(s.getKey());
                if (humans == null || humans.isEmpty()) {
                    continue; // 면접관 점수가 없는 항목은 비교하지 않는다.
                }
                double human = StatMath.rawMean(humans);
                diffs.add(s.getValue() - human);
                List<double[]> pairs = pairsByCode.get(s.getKey());
                if (pairs == null) {
                    pairs = new ArrayList<double[]>();
                    pairsByCode.put(s.getKey(), pairs);
                }
                pairs.add(new double[] { s.getValue(), human });
            }
        }

        QualityDtos.AiCoverage coverage = response.getAiCoverage();
        coverage.setExpected(expected);
        coverage.setScored(scored);
        coverage.setRate(StatMath.rate(scored, expected));

        QualityDtos.ScoreGap gap = response.getScoreGap();
        gap.setComparedCount(diffs.size());
        gap.setMeanDiff(StatMath.mean(diffs));
        gap.setMeanAbsDiff(StatMath.mean(abs(diffs)));
        long within = 0;
        for (Double d : diffs) {
            if (Math.abs(d) <= 1.0) {
                within++;
            }
        }
        gap.setWithinOneRate(StatMath.rate(within, diffs.size()));
        for (Map.Entry<String, List<double[]>> e : pairsByCode.entrySet()) {
            List<Double> aiValues = new ArrayList<Double>();
            List<Double> humanValues = new ArrayList<Double>();
            List<Double> codeDiffs = new ArrayList<Double>();
            for (double[] pair : e.getValue()) {
                aiValues.add(pair[0]);
                humanValues.add(pair[1]);
                codeDiffs.add(pair[0] - pair[1]);
            }
            QualityDtos.CriterionGap item = new QualityDtos.CriterionGap();
            item.setCriterionCode(e.getKey());
            item.setCriterionName(criterionNames.get(e.getKey()));
            item.setComparedCount(codeDiffs.size());
            item.setAiMean(StatMath.mean(aiValues));
            item.setInterviewerMean(StatMath.mean(humanValues));
            item.setMeanDiff(StatMath.mean(codeDiffs));
            item.setMeanAbsDiff(StatMath.mean(abs(codeDiffs)));
            gap.getByCriterion().add(item);
        }
    }

    private static List<Double> abs(List<Double> values) {
        List<Double> result = new ArrayList<Double>();
        for (Double v : values) {
            result.add(Math.abs(v));
        }
        return result;
    }

    // ------------------------------------------------------------------ 건수 집계

    private void fillFollowUps(QualityDtos.FollowUps target, Long recruitmentId) {
        List<Object[]> rows = followUpRepository.summarizeOfRecruitment(recruitmentId);
        if (!rows.isEmpty()) {
            Object[] row = rows.get(0);
            target.setSuggested(number(row[0]));
            target.setUsed(number(row[1]));
            target.setRoomCount(number(row[2]));
        }
        target.setAdoptionRate(StatMath.rate(target.getUsed(), target.getSuggested()));
    }

    private void fillSpeaker(QualityDtos.Speaker target, Long recruitmentId) {
        Map<SpeakerMethod, Long> byMethod = new EnumMap<SpeakerMethod, Long>(SpeakerMethod.class);
        long total = 0;
        long unknown = 0;
        for (Object[] row : utteranceRepository.countBySpeakerOfRecruitment(recruitmentId)) {
            SpeakerMethod method = (SpeakerMethod) row[0];
            long count = number(row[2]);
            total += count;
            if (row[1] == Speaker.UNKNOWN) {
                unknown += count;
            }
            byMethod.put(method, (byMethod.containsKey(method) ? byMethod.get(method) : 0L) + count);
        }
        for (SpeakerMethod method : SpeakerMethod.values()) {
            target.getByMethod().put(method.name(), byMethod.containsKey(method) ? byMethod.get(method) : 0L);
        }
        target.setUtteranceCount(total);
        target.setManualRate(StatMath.rate(target.getByMethod().get(SpeakerMethod.MANUAL.name()), total));
        target.setUnknownRate(StatMath.rate(unknown, total));
    }

    private void fillQuestions(QualityDtos.Questions target, Long recruitmentId) {
        List<Object[]> rows = questionRepository.summarizePreparedOfRecruitment(recruitmentId, PREPARED);
        if (!rows.isEmpty()) {
            target.setPrepared(number(rows.get(0)[0]));
            target.setAsked(number(rows.get(0)[1]));
        }
        target.setAutoMatched(questionRepository.countAutoMatchedOfRecruitment(recruitmentId, PREPARED));
        target.setAskedRate(StatMath.rate(target.getAsked(), target.getPrepared()));
        target.setAutoMatchedRate(StatMath.rate(target.getAutoMatched(), target.getAsked()));

        // 출처별: 어떤 종류의 준비 질문이 실제로 더 쓰였는지 비교한다. 질문이 없는 출처도 0으로 내보낸다.
        Map<QuestionSource, Object[]> bySource = new HashMap<QuestionSource, Object[]>();
        for (Object[] row : questionRepository.summarizePreparedBySourceOfRecruitment(recruitmentId, PREPARED)) {
            bySource.put((QuestionSource) row[0], row);
        }
        for (QuestionSource source : PREPARED) {
            Object[] row = bySource.get(source);
            QualityDtos.SourceUsage usage = new QualityDtos.SourceUsage();
            usage.setSource(source.name());
            usage.setPrepared(row == null ? 0 : number(row[1]));
            usage.setAsked(row == null ? 0 : number(row[2]));
            usage.setAskedRate(StatMath.rate(usage.getAsked(), usage.getPrepared()));
            target.getBySource().add(usage);
        }
    }

    /** 방 종료 → 그 면접의 첫 LIVE / 첫 REFINED 보고서까지 걸린 시간(초). */
    private void fillReports(QualityDtos.Reports target, Long recruitmentId, List<Room> rooms) {
        Map<Long, LocalDateTime> endedAtByInterview = new HashMap<Long, LocalDateTime>();
        for (Room room : rooms) {
            if (room.getEndedAt() != null) {
                endedAtByInterview.put(room.getInterviewId(), room.getEndedAt());
            }
        }
        List<Long> liveSeconds = new ArrayList<Long>();
        List<Long> refinedSeconds = new ArrayList<Long>();
        for (Object[] row : reportRepository.firstGeneratedOfRecruitment(recruitmentId)) {
            LocalDateTime endedAt = endedAtByInterview.get((Long) row[0]);
            LocalDateTime generatedAt = (LocalDateTime) row[2];
            if (endedAt == null || generatedAt == null) {
                continue;
            }
            long seconds = Math.max(0, Duration.between(endedAt, generatedAt).getSeconds());
            if (row[1] == ReportBasis.LIVE) {
                liveSeconds.add(seconds);
            } else if (row[1] == ReportBasis.REFINED) {
                refinedSeconds.add(seconds);
            }
        }
        target.setLiveCount(liveSeconds.size());
        target.setRefinedCount(refinedSeconds.size());
        target.setLiveSecondsMedian(StatMath.median(liveSeconds));
        target.setLiveSecondsMax(StatMath.max(liveSeconds));
        target.setRefinedSecondsMedian(StatMath.median(refinedSeconds));
        target.setRefinedSecondsMax(StatMath.max(refinedSeconds));
    }

    private void fillJobs(List<QualityDtos.JobCount> target, Long recruitmentId) {
        Map<JobType, QualityDtos.JobCount> byType = new EnumMap<JobType, QualityDtos.JobCount>(JobType.class);
        for (JobType type : JobType.values()) {
            QualityDtos.JobCount count = new QualityDtos.JobCount(type);
            byType.put(type, count);
            target.add(count);
        }
        for (Object[] row : aiJobRepository.countByTypeAndStatusOfRecruitment(recruitmentId)) {
            QualityDtos.JobCount count = byType.get((JobType) row[0]);
            if (row[1] == JobStatus.SUCCEEDED) {
                count.setSucceeded(number(row[2]));
            } else if (row[1] == JobStatus.FAILED) {
                count.setFailed(number(row[2]));
            }
        }
    }

    private static long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
