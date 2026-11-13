package com.interview.report;

import com.interview.recruitment.Track;

/** 보고서의 상세도. BRIEF = 신입, DETAILED = 경력. */
public enum ReportDetail {
    BRIEF, DETAILED;

    public static ReportDetail of(Track track) {
        return track == Track.EXPERIENCED ? DETAILED : BRIEF;
    }
}
