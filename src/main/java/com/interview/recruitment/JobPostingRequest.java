package com.interview.recruitment;

import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 직무 등록(POST)·수정(PUT) 본문. 수정에서는 jobCode 를 바꾸지 않는다(면접자가 코드로 물려 있음). */
@Getter
@Setter
public class JobPostingRequest {

    @Size(max = 30)
    private String jobCode;

    @Size(max = 200)
    private String title;

    private String jdText;
}
