package com.interview.recruitment;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;
import lombok.Setter;

/** 채용 응답. 집계 세 필드는 상세(GET /recruitments/{id})에서만 채운다. */
@Getter
@Setter
public class RecruitmentDto {

    private Long id;
    private String title;
    private Track track;
    private LocalDate startDate;
    private LocalDate endDate;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long candidateCount;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long interviewCount;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long doneCount;

    public static RecruitmentDto from(Recruitment r) {
        RecruitmentDto dto = new RecruitmentDto();
        dto.id = r.getId();
        dto.title = r.getTitle();
        dto.track = r.getTrack();
        dto.startDate = r.getStartDate();
        dto.endDate = r.getEndDate();
        return dto;
    }
}
