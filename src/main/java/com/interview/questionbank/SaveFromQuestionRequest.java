package com.interview.questionbank;

import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 면접에서 쓴 질문을 은행에 담을 때의 본문. 비워 두면 그 질문의 글과 분류를 그대로 쓴다. */
@Getter
@Setter
public class SaveFromQuestionRequest {

    /** 은행에 넣을 글. 그 지원자에게만 맞는 표현을 일반적인 말로 고쳐 담을 수 있다. */
    @Size(max = 1000)
    private String text;

    @Size(max = 100)
    private String category;
}
