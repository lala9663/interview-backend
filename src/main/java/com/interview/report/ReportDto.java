package com.interview.report;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.common.Times;
import com.interview.job.JobStatus;

import lombok.Getter;

/**
 * 계약 2.8 의 Report.
 * 보고서가 아직 없으면 version·content 등은 null 이고 status 는 가장 최근 보고서 작업의 상태(없으면 null)다.
 */
@Getter
public class ReportDto {

    private final Integer version;
    private final ReportBasis basis;
    private final ReportDetail detail;
    private final JobStatus status;
    private final OffsetDateTime generatedAt;
    private final String model;
    private final JsonNode content;

    private ReportDto(Report r, JobStatus status) {
        this.version = r == null ? null : r.getVersion();
        this.basis = r == null ? null : r.getBasis();
        this.detail = r == null ? null : r.getDetail();
        this.status = status;
        this.generatedAt = r == null ? null : Times.toOffset(r.getGeneratedAt());
        this.model = r == null ? null : r.getModel();
        this.content = r == null ? null : r.getContent();
    }

    public static ReportDto of(Report r) {
        return new ReportDto(r, JobStatus.SUCCEEDED);
    }

    public static ReportDto none(JobStatus latestJobStatus) {
        return new ReportDto(null, latestJobStatus);
    }
}
