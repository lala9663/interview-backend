package com.interview.matching;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.interview.candidate.Candidate;
import com.interview.interview.Interview;
import com.interview.interview.MatchedBy;
import com.interview.user.UserAccount;

/**
 * JD(직무 코드) 기반 임시 매칭: 면접관의 jobCodes 에 면접자의 jobCode 가 있으면 후보.
 * 후보 가운데 지금까지 배정이 적은 사람부터 뽑는다(부하 균등, 같으면 id 순).
 */
@Component
@ConditionalOnProperty(name = "app.matching.strategy", havingValue = "jd", matchIfMissing = true)
public class JdBasedInterviewerMatcher implements InterviewerMatcher {

    @Override
    public MatchedBy matchedBy() {
        return MatchedBy.JD;
    }

    @Override
    public List<UserAccount> pick(Candidate candidate, Interview interview, List<UserAccount> pool,
            final Map<Long, Integer> load, int count) {
        List<UserAccount> eligible = new ArrayList<UserAccount>();
        for (UserAccount u : pool) {
            if (u.getJobCodes().contains(candidate.getJobCode())) {
                eligible.add(u);
            }
        }
        Collections.sort(eligible, new Comparator<UserAccount>() {
            @Override
            public int compare(UserAccount a, UserAccount b) {
                int la = load.containsKey(a.getId()) ? load.get(a.getId()) : 0;
                int lb = load.containsKey(b.getId()) ? load.get(b.getId()) : 0;
                if (la != lb) {
                    return la < lb ? -1 : 1;
                }
                return a.getId().compareTo(b.getId());
            }
        });
        return new ArrayList<UserAccount>(eligible.subList(0, Math.min(count, eligible.size())));
    }
}
