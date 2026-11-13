package com.interview.recruitment;

import lombok.Getter;

@Getter
public class JobPostingDto {

    private final Long id;
    private final Long recruitmentId;
    private final String jobCode;
    private final String title;
    private final String jdText;

    private JobPostingDto(JobPosting j) {
        this.id = j.getId();
        this.recruitmentId = j.getRecruitmentId();
        this.jobCode = j.getJobCode();
        this.title = j.getTitle();
        this.jdText = j.getJdText();
    }

    public static JobPostingDto from(JobPosting j) {
        return new JobPostingDto(j);
    }
}
