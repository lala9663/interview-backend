package com.interview.evaluation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** app.evaluation.* 설정. */
@ConfigurationProperties(prefix = "app.evaluation")
@Getter
@Setter
public class EvaluationProperties {

    /** AI 참고 평가(GET /interviews/{id}/ai-evaluation)를 볼 수 있는 사람. 기본은 ADMIN(면접관은 403). */
    private Audience aiVisibleTo = Audience.ADMIN;

    public enum Audience {
        /** 관리자만. */
        ADMIN,
        /** 로그인한 사용자 모두. */
        ALL
    }
}
