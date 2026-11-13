package com.interview.recruitment;

import java.time.LocalDate;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecruitmentRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200)
    private String title;

    @NotNull(message = "트랙은 필수입니다.")
    private Track track;

    private LocalDate startDate;
    private LocalDate endDate;
}
