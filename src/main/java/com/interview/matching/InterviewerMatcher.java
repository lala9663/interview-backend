package com.interview.matching;

import java.util.List;
import java.util.Map;

import com.interview.candidate.Candidate;
import com.interview.interview.Interview;
import com.interview.interview.MatchedBy;
import com.interview.user.UserAccount;

/**
 * 면접관을 고르는 규칙의 교체 지점. app.matching.strategy 로 고른다.
 * - jd: {@link JdBasedInterviewerMatcher} (지금의 임시 구현)
 * - 나중에 엑셀 import 방식(현업이 만든 배정표를 그대로 넣기)으로 바꾸려면 이 인터페이스의 구현을 하나 더 만들고
 *   matchedBy() 에서 IMPORT 를 돌려준다.
 */
public interface InterviewerMatcher {

    MatchedBy matchedBy();

    /**
     * 면접 한 건에 배정할 면접관을 고른다.
     *
     * @param pool  활성 사용자 전체
     * @param load  사용자 id → 이 채용에서 이미 배정된 면접 수 (부하 균등에 쓴다. 읽기만 한다)
     * @param count 뽑을 인원
     * @return 0명 이상 count 명 이하
     */
    List<UserAccount> pick(Candidate candidate, Interview interview, List<UserAccount> pool, Map<Long, Integer> load,
            int count);
}
