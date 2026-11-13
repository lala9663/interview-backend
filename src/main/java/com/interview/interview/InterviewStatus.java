package com.interview.interview;

/**
 * 면접 상태.
 * WAITING(일정 전) → SCHEDULED → IN_PROGRESS(방 시작) → EVALUATING(방 종료) → DONE(배정된 면접관이 모두 평가 제출).
 */
public enum InterviewStatus {
    WAITING, SCHEDULED, IN_PROGRESS, EVALUATING, DONE, CANCELED
}
