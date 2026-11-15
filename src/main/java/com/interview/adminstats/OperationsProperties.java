package com.interview.adminstats;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** app.operations.* — 운영 현황판의 주의(alerts) 기준. API 명세 2.11 의 표와 같은 기본값이다. */
@ConfigurationProperties(prefix = "app.operations")
@Getter
@Setter
public class OperationsProperties {

    /** 예정 시각이 이만큼(분) 지났는데 시작하지 않으면 NOT_STARTED. */
    private int notStartedMinutes = 5;

    /** 예정 길이를 이만큼(분) 넘겨 진행 중이면 OVERTIME. */
    private int overtimeMinutes = 5;

    /** 진행 중인데 전사가 이만큼(분) 없으면 SILENT. */
    private int silentMinutes = 3;

    /** Agent 의 방 큐 깊이가 이 값 이상이면 STT_BACKLOG (건너뛴 구간은 1개만 있어도). */
    private int sttBacklogDepth = 5;
}
