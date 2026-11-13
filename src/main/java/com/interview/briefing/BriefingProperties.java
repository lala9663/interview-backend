package com.interview.briefing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** app.briefing.* 설정. */
@ConfigurationProperties(prefix = "app.briefing")
@Getter
@Setter
public class BriefingProperties {

    /** Agent 에 요청하는 질문 수(JD 기반 / 이력서 기반). */
    private int jdQuestionCount = 4;
    private int resumeQuestionCount = 6;

    /** Agent 에 넘기는 질문 은행 항목 수의 상한. */
    private int questionBankLimit = 30;
}
