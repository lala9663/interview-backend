package com.interview.adminstats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interview.common.Times;
import com.interview.evaluation.EvalCriterion;
import com.interview.evaluation.EvalCriterionRepository;
import com.interview.evaluation.InterviewerEvaluation;
import com.interview.evaluation.InterviewerEvaluationRepository;
import com.interview.interview.Interview;
import com.interview.interview.InterviewAssignee;
import com.interview.interview.InterviewAssigneeRepository;
import com.interview.interview.InterviewRepository;
import com.interview.interview.InterviewStatus;
import com.interview.recruitment.RecruitmentService;
import com.interview.user.UserAccount;
import com.interview.user.UserService;

import lombok.RequiredArgsConstructor;

/**
 * 면접관 편차(계약 2.11 의 /admin/interviewer-stats).
 * 채용 한 건의 면접·배정·제출된 평가를 한 번씩 읽어 자바에서 계산한다(점수가 JSON 이라 DB 에서 집계할 수 없다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewerStatsService {

    private final RecruitmentService recruitmentService;
    private final InterviewRepository interviewRepository;
    private final InterviewAssigneeRepository assigneeRepository;
    private final InterviewerEvaluationRepository evaluationRepository;
    private final EvalCriterionRepository criterionRepository;
    private final UserService userService;

    public InterviewerStatsDtos.Response stats(Long recruitmentId) {
        recruitmentService.get(recruitmentId);
        Map<Long, Interview> interviews = new HashMap<Long, Interview>();
        for (Interview i : interviewRepository.findByRecruitmentIdOrderByCandidateIdAscSeqAsc(recruitmentId)) {
            interviews.put(i.getId(), i);
        }
        List<InterviewAssignee> assignees = assigneeRepository.findByRecruitmentId(recruitmentId);
        List<InterviewerEvaluation> evaluations = evaluationRepository.findSubmittedByRecruitmentId(recruitmentId);
        Map<String, String> criterionNames = new HashMap<String, String>();
        int maxScore = 5;
        for (EvalCriterion c : criterionRepository.findAll()) {
            if (!criterionNames.containsKey(c.getCode())) {
                criterionNames.put(c.getCode(), c.getName());
            }
            maxScore = Math.max(maxScore, c.getMaxScore());
        }

        // 제출된 평가를 면접관별·면접별로 정리한다.
        Map<Long, List<InterviewerEvaluation>> byUser = new HashMap<Long, List<InterviewerEvaluation>>();
        Map<Long, Map<Long, Double>> meanByInterviewAndUser = new HashMap<Long, Map<Long, Double>>();
        Set<String> submittedKeys = new HashSet<String>();
        List<Double> allScores = new ArrayList<Double>();
        for (InterviewerEvaluation e : evaluations) {
            List<InterviewerEvaluation> list = byUser.get(e.getUserId());
            if (list == null) {
                list = new ArrayList<InterviewerEvaluation>();
                byUser.put(e.getUserId(), list);
            }
            list.add(e);
            submittedKeys.add(e.getInterviewId() + ":" + e.getUserId());
            List<Double> scores = new ArrayList<Double>(ScoreJson.scores(e.getScores()).values());
            allScores.addAll(scores);
            Double mean = StatMath.rawMean(scores);
            if (mean != null) {
                Map<Long, Double> byUserMean = meanByInterviewAndUser.get(e.getInterviewId());
                if (byUserMean == null) {
                    byUserMean = new HashMap<Long, Double>();
                    meanByInterviewAndUser.put(e.getInterviewId(), byUserMean);
                }
                byUserMean.put(e.getUserId(), mean);
            }
        }

        InterviewerStatsDtos.Response response = new InterviewerStatsDtos.Response();
        response.setGeneratedAt(Times.toOffset(Times.now()));
        response.getOverall().setEvaluationCount(evaluations.size());
        Double overallRawMean = StatMath.rawMean(allScores);
        response.getOverall().setMeanScore(StatMath.mean(allScores));
        response.getOverall().setStdDev(StatMath.stdDev(allScores));

        // 대상: 배정이 있거나 제출한 평가가 있는 사용자
        Map<Long, List<InterviewAssignee>> assignedByUser = new HashMap<Long, List<InterviewAssignee>>();
        Set<Long> userIds = new LinkedHashSet<Long>();
        for (InterviewAssignee a : assignees) {
            Interview interview = interviews.get(a.getInterviewId());
            if (interview == null || interview.getStatus() == InterviewStatus.CANCELED) {
                continue;
            }
            List<InterviewAssignee> list = assignedByUser.get(a.getUserId());
            if (list == null) {
                list = new ArrayList<InterviewAssignee>();
                assignedByUser.put(a.getUserId(), list);
            }
            list.add(a);
            userIds.add(a.getUserId());
        }
        userIds.addAll(byUser.keySet());
        Map<Long, UserAccount> users = userService.mapByIds(userIds);

        for (Long userId : userIds) {
            InterviewerStatsDtos.Item item = new InterviewerStatsDtos.Item();
            UserAccount user = users.get(userId);
            item.setInterviewer(new InterviewerStatsDtos.InterviewerRef(userId, user == null ? null : user.getName(),
                    user == null ? null : user.getDepartment()));

            List<InterviewAssignee> assigned = assignedByUser.get(userId);
            long pending = 0;
            if (assigned != null) {
                item.setAssignedCount(assigned.size());
                for (InterviewAssignee a : assigned) {
                    InterviewStatus status = interviews.get(a.getInterviewId()).getStatus();
                    boolean ended = status == InterviewStatus.EVALUATING || status == InterviewStatus.DONE;
                    if (ended && !submittedKeys.contains(a.getInterviewId() + ":" + userId)) {
                        pending++;
                    }
                }
            }
            item.setPendingCount(pending);

            List<InterviewerEvaluation> mine = byUser.get(userId);
            List<Double> myScores = new ArrayList<Double>();
            Map<String, List<Double>> byCode = new TreeMap<String, List<Double>>();
            for (int score = 1; score <= maxScore; score++) {
                item.getScoreDistribution().put(String.valueOf(score), 0L);
            }
            List<Double> peerDiffs = new ArrayList<Double>();
            if (mine != null) {
                item.setSubmittedCount(mine.size());
                for (InterviewerEvaluation e : mine) {
                    for (Map.Entry<String, Double> s : ScoreJson.scores(e.getScores()).entrySet()) {
                        myScores.add(s.getValue());
                        List<Double> list = byCode.get(s.getKey());
                        if (list == null) {
                            list = new ArrayList<Double>();
                            byCode.put(s.getKey(), list);
                        }
                        list.add(s.getValue());
                        String key = String.valueOf(Math.round(s.getValue()));
                        Long count = item.getScoreDistribution().get(key);
                        item.getScoreDistribution().put(key, (count == null ? 0L : count) + 1);
                    }
                    if (e.getGrade() != null && !e.getGrade().trim().isEmpty()) {
                        String grade = e.getGrade().trim();
                        Long count = item.getGradeDistribution().get(grade);
                        item.getGradeDistribution().put(grade, (count == null ? 0L : count) + 1);
                    }
                    // 같은 면접을 본 다른 면접관들의 평균(면접관별 그 면접 평균의 평균)과의 차이
                    Map<Long, Double> means = meanByInterviewAndUser.get(e.getInterviewId());
                    if (means != null && means.containsKey(userId)) {
                        List<Double> others = new ArrayList<Double>();
                        for (Map.Entry<Long, Double> m : means.entrySet()) {
                            if (!m.getKey().equals(userId)) {
                                others.add(m.getValue());
                            }
                        }
                        if (!others.isEmpty()) {
                            peerDiffs.add(means.get(userId) - StatMath.rawMean(others));
                        }
                    }
                }
            }
            Double myRawMean = StatMath.rawMean(myScores);
            item.setMeanScore(StatMath.mean(myScores));
            item.setStdDev(StatMath.stdDev(myScores));
            if (myRawMean != null && overallRawMean != null) {
                item.setDeviationFromOverall(StatMath.round2(myRawMean - overallRawMean));
            }
            item.setDeviationFromPeers(StatMath.mean(peerDiffs));
            item.setPeerComparedCount(peerDiffs.size());
            for (Map.Entry<String, List<Double>> c : byCode.entrySet()) {
                item.getByCriterion().add(new InterviewerStatsDtos.CriterionMean(c.getKey(), criterionNames.get(c.getKey()),
                        StatMath.mean(c.getValue())));
            }
            response.getItems().add(item);
        }

        // 제출이 많은 순, 같으면 이름 순
        Collections.sort(response.getItems(), new Comparator<InterviewerStatsDtos.Item>() {
            @Override
            public int compare(InterviewerStatsDtos.Item a, InterviewerStatsDtos.Item b) {
                if (a.getSubmittedCount() != b.getSubmittedCount()) {
                    return a.getSubmittedCount() > b.getSubmittedCount() ? -1 : 1;
                }
                String an = a.getInterviewer().getName() == null ? "" : a.getInterviewer().getName();
                String bn = b.getInterviewer().getName() == null ? "" : b.getInterviewer().getName();
                int byName = an.compareTo(bn);
                return byName != 0 ? byName : a.getInterviewer().getId().compareTo(b.getInterviewer().getId());
            }
        });
        return response;
    }
}
