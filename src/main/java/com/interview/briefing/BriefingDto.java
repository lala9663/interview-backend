package com.interview.briefing;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.common.Times;
import com.interview.job.JobStatus;
import com.interview.report.PreviousRoundChecks;

import lombok.Getter;

/** API 명세 2.6 의 Briefing. 아직 없으면 status 가 null 이다. */
@Getter
public class BriefingDto {

    private final JobStatus status;
    private final OffsetDateTime generatedAt;
    private final String model;
    private final JsonNode summary;
    private final List<String> excluded;
    /** 같은 면접자의 바로 앞 차수 보고서의 nextRoundChecks. 없으면 null. */
    private final PreviousRoundChecks previousRoundChecks;

    private BriefingDto(Briefing b, PreviousRoundChecks previousRoundChecks) {
        this.status = b == null ? null : b.getStatus();
        this.generatedAt = b == null ? null : Times.toOffset(b.getGeneratedAt());
        this.model = b == null ? null : b.getModel();
        this.summary = b == null ? null : b.getSummary();
        this.excluded = b == null ? new ArrayList<String>() : new ArrayList<String>(b.getExcluded());
        this.previousRoundChecks = previousRoundChecks;
    }

    public static BriefingDto from(Briefing b, PreviousRoundChecks previousRoundChecks) {
        return new BriefingDto(b, previousRoundChecks);
    }
}
