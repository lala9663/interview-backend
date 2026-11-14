package com.interview.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.interview.recruitment.Track;

import lombok.Getter;
import lombok.Setter;

/** app.agent.* 설정. */
@ConfigurationProperties(prefix = "app.agent")
@Getter
@Setter
public class AgentProperties {

    /** http | fake */
    private String mode = "http";

    /** 백엔드 → Agent REST 주소. */
    private String baseUrl = "http://localhost:8000";

    /** 프론트가 붙는 Agent WebSocket 주소(입장권 응답의 agentWsUrl 을 만들 때 쓴다). */
    private String wsBaseUrl = "ws://localhost:8000";

    private int connectTimeoutSeconds = 3;
    private int healthTimeoutSeconds = 2;
    private int finalizeTimeoutSeconds = 30;
    /** 운영 현황판이 부르는 /internal/v1/stats 의 제한 시간. 화면 조회라 짧게 둔다. */
    private int statsTimeoutSeconds = 3;

    /** 트랙별 제한 시간. 신입은 1~2분 안에 초안이 나와야 하고, 경력 상세 보고서는 오래 걸려도 된다. */
    private Timeouts newGrad = new Timeouts(120, 120, 120);
    private Timeouts experienced = new Timeouts(300, 600, 3600);

    public Timeouts timeouts(Track track) {
        return track == Track.EXPERIENCED ? experienced : newGrad;
    }

    @Getter
    @Setter
    public static class Timeouts {
        private int briefingSeconds;
        private int evaluationSeconds;
        private int reportSeconds;

        public Timeouts() {
        }

        public Timeouts(int briefingSeconds, int evaluationSeconds, int reportSeconds) {
            this.briefingSeconds = briefingSeconds;
            this.evaluationSeconds = evaluationSeconds;
            this.reportSeconds = reportSeconds;
        }
    }
}
