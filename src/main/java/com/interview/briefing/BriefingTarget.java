package com.interview.briefing;

import com.interview.common.ApiException;

/**
 * 사전 리포트 작업이 무엇을 만드는가(API 명세 2.6 의 target).
 * ALL: 요약과 질문 모두. SUMMARY: 요약만 바꾸고 질문은 그대로 둔다. QUESTIONS: 질문만 바꾸고 요약은 그대로 둔다.
 */
public enum BriefingTarget {
    ALL, SUMMARY, QUESTIONS;

    public boolean summary() {
        return this != QUESTIONS;
    }

    public boolean questions() {
        return this != SUMMARY;
    }

    /** 요청 값. 없으면 ALL, 모르는 값이면 400. */
    public static BriefingTarget parse(Object value) {
        if (value == null) {
            return ALL;
        }
        try {
            return valueOf(String.valueOf(value).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("target 은 ALL, SUMMARY, QUESTIONS 가운데 하나여야 합니다.");
        }
    }

    /** 작업에 적어 둔 값. 없거나 모르는 값이면 ALL(예전에 접수된 작업). */
    public static BriefingTarget ofJob(String value) {
        if (value == null) {
            return ALL;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }
}
