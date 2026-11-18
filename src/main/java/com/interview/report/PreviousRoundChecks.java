package com.interview.report;

import java.util.List;

import com.interview.interview.InterviewType;

import lombok.Getter;

/** API 명세 2.6 Briefing 의 previousRoundChecks: 바로 앞 차수 보고서가 남긴 "다음 차수에서 확인할 점". */
@Getter
public class PreviousRoundChecks {

    private final int seq;
    private final InterviewType type;
    private final List<String> items;

    public PreviousRoundChecks(int seq, InterviewType type, List<String> items) {
        this.seq = seq;
        this.type = type;
        this.items = items;
    }
}
