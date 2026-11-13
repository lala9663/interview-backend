package com.interview.matching;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interview.briefing.BriefingService;
import com.interview.candidate.Candidate;
import com.interview.candidate.CandidateRepository;
import com.interview.interview.Interview;
import com.interview.interview.InterviewAssignee;
import com.interview.interview.InterviewAssigneeRepository;
import com.interview.interview.InterviewRepository;
import com.interview.interview.InterviewService;
import com.interview.interview.InterviewStatus;
import com.interview.interview.InterviewType;
import com.interview.recruitment.RecruitmentService;
import com.interview.user.UserAccount;
import com.interview.user.UserService;

import lombok.RequiredArgsConstructor;

/**
 * 자동 매칭(계약 2.4, 흐름 6장 "면접 전" 2단계).
 * 1) 면접이 하나도 없는 면접자에게 트랙별 기본 면접을 만든다(app.matching.*-plan, 상태 WAITING).
 * 2) 면접관이 없는 면접(끝났거나 취소된 것 제외)에 {@link InterviewerMatcher} 로 면접관을 배정한다.
 * 3) app.features.auto-briefing-on-match 가 켜져 있으면 새로 만든 면접의 브리핑 작업을 접수한다.
 * 여러 번 실행해도 이미 배정된 면접은 건드리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AutoAssignmentService {

    private final RecruitmentService recruitmentService;
    private final CandidateRepository candidateRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewAssigneeRepository assigneeRepository;
    private final UserService userService;
    private final InterviewerMatcher matcher;
    private final MatchingProperties props;
    private final BriefingService briefingService;

    @Value("${app.features.auto-briefing-on-match:true}")
    private boolean autoBriefing;

    @Transactional
    public AutoAssignResult run(Long recruitmentId) {
        recruitmentService.get(recruitmentId);
        List<Candidate> candidates = candidateRepository.findByRecruitmentIdOrderByApplicantNoAsc(recruitmentId);
        List<UserAccount> pool = userService.findInterviewers(null);

        Map<Long, List<Interview>> interviewsByCandidate = new HashMap<Long, List<Interview>>();
        for (Interview i : interviewRepository.findByRecruitmentIdOrderByCandidateIdAscSeqAsc(recruitmentId)) {
            List<Interview> list = interviewsByCandidate.get(i.getCandidateId());
            if (list == null) {
                list = new ArrayList<Interview>();
                interviewsByCandidate.put(i.getCandidateId(), list);
            }
            list.add(i);
        }
        Map<Long, Integer> load = new HashMap<Long, Integer>();
        Set<Long> alreadyAssigned = new HashSet<Long>();
        for (InterviewAssignee a : assigneeRepository.findByRecruitmentId(recruitmentId)) {
            alreadyAssigned.add(a.getInterviewId());
            load.put(a.getUserId(), (load.containsKey(a.getUserId()) ? load.get(a.getUserId()) : 0) + 1);
        }

        AutoAssignResult result = new AutoAssignResult();
        result.setCandidateCount(candidates.size());
        List<Long> created = new ArrayList<Long>();
        for (Candidate candidate : candidates) {
            List<Interview> interviews = interviewsByCandidate.get(candidate.getId());
            if (interviews == null) {
                interviews = new ArrayList<Interview>();
                int seq = 0;
                for (InterviewType type : props.plan(candidate.getTrack())) {
                    Interview interview = interviewRepository.save(InterviewService.newInterview(candidate, type, ++seq));
                    interviews.add(interview);
                    created.add(interview.getId());
                }
            }
            for (Interview interview : interviews) {
                if (alreadyAssigned.contains(interview.getId()) || interview.getStatus() == InterviewStatus.DONE
                        || interview.getStatus() == InterviewStatus.CANCELED) {
                    continue;
                }
                List<UserAccount> picked = matcher.pick(candidate, interview, pool, load, props.getInterviewersPerInterview());
                if (picked.isEmpty()) {
                    result.setUnassignedInterviewCount(result.getUnassignedInterviewCount() + 1);
                    result.getUnassigned().add(new AutoAssignResult.Unassigned(interview.getId(), candidate.getId(),
                            candidate.getName(), candidate.getJobCode(), interview.getType()));
                    continue;
                }
                for (UserAccount u : picked) {
                    assigneeRepository.save(new InterviewAssignee(interview.getId(), u.getId(), matcher.matchedBy()));
                    load.put(u.getId(), (load.containsKey(u.getId()) ? load.get(u.getId()) : 0) + 1);
                }
                result.setAssignedInterviewCount(result.getAssignedInterviewCount() + 1);
            }
        }
        result.setCreatedInterviewCount(created.size());
        if (autoBriefing) {
            for (Long interviewId : created) {
                briefingService.requestGenerate(interviewId);
            }
            result.setBriefingJobCount(created.size());
        }
        return result;
    }
}
