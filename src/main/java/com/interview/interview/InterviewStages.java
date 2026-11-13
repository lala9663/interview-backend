package com.interview.interview;

import java.util.List;

/** 면접자의 "지금 차수"를 고르는 규칙. */
public final class InterviewStages {

    private InterviewStages() {
    }

    /**
     * seq 순으로 정렬된 면접 목록에서 지금 차수를 고른다.
     * 끝나지 않은(DONE·CANCELED 가 아닌) 첫 면접, 모두 끝났으면 마지막으로 끝난 면접, 없으면 null.
     */
    public static Interview current(List<Interview> sortedBySeq) {
        Interview lastDone = null;
        for (Interview i : sortedBySeq) {
            if (i.getStatus() == InterviewStatus.CANCELED) {
                continue;
            }
            if (i.getStatus() != InterviewStatus.DONE) {
                return i;
            }
            lastDone = i;
        }
        return lastDone;
    }
}
