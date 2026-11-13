package com.interview.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** app.jobs.* 설정. */
@ConfigurationProperties(prefix = "app.jobs")
@Getter
@Setter
public class JobProperties {

    /** 동시에 돌리는 AI 작업 수. 시즌에 동시 20개 이상의 면접이 끝날 수 있다. */
    private int corePoolSize = 8;
    private int maxPoolSize = 24;
    private int queueCapacity = 2000;

    /**
     * 서버가 다시 뜰 때 PENDING/RUNNING 으로 남은 자기 작업을 어떻게 할지.
     * fail = FAILED(SERVER_RESTARTED)로 바꾼다(기본). restart = 다시 실행한다.
     */
    private String recovery = "fail";

    /** 이 서버의 이름. 비우면 호스트 이름. 여러 대를 띄울 때 서로의 작업을 건드리지 않게 한다. */
    private String instanceId;
}
