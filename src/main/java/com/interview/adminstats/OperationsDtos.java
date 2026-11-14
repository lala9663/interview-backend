package com.interview.adminstats;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.interview.interview.InterviewDto;

import lombok.Getter;
import lombok.Setter;

/** GET /admin/operations 응답(계약 2.11). */
public final class OperationsDtos {

    public static final String NOT_STARTED = "NOT_STARTED";
    public static final String OVERTIME = "OVERTIME";
    public static final String NO_AUDIO = "NO_AUDIO";
    public static final String SILENT = "SILENT";
    public static final String STT_BACKLOG = "STT_BACKLOG";
    public static final String JOB_FAILED = "JOB_FAILED";
    public static final String AGENT_DOWN = "AGENT_DOWN";

    private OperationsDtos() {
    }

    @Getter
    @Setter
    public static class Response {
        private LocalDate date;
        private OffsetDateTime generatedAt;
        private Agent agent;
        private Summary summary = new Summary();
        private List<Item> items = new ArrayList<Item>();
    }

    @Getter
    @Setter
    public static class Agent {
        /** UP | DOWN */
        private String status;
        /** Agent 가 맡고 있는 방 수. DOWN 이면 null. */
        private Integer rooms;
        private List<Provider> providers = new ArrayList<Provider>();
    }

    @Getter
    public static class Provider {
        private final String role;
        private final String health;
        private final int waiting;

        public Provider(String role, String health, int waiting) {
            this.role = role;
            this.health = health;
            this.waiting = waiting;
        }
    }

    @Getter
    @Setter
    public static class Summary {
        private int total;
        private int waiting;
        private int scheduled;
        private int live;
        private int evaluating;
        private int done;
        private int attention;
    }

    @Getter
    @Setter
    public static class Item {
        private InterviewDto interview;
        private OffsetDateTime startedAt;
        private OffsetDateTime endedAt;
        /** 방을 시작한 뒤 지난 시간(LIVE), 끝났으면 진행한 시간. 아직 시작하지 않았으면 null. */
        private Long elapsedMinutes;
        private long lateMinutes;
        private long overtimeMinutes;
        private long utteranceCount;
        private OffsetDateTime lastUtteranceAt;
        /** LIVE 일 때 마지막 전사(없으면 시작) 뒤 지난 시간. LIVE 가 아니면 0. */
        private long silentMinutes;
        /** Agent 가 아는 마이크 연결 상태. Agent 가 그 방을 모르면 null. */
        private Boolean audioConnected;
        /** Agent 가 그 방을 모르면 0. */
        private int queueDepth;
        private int droppedChunks;
        private List<String> alerts = new ArrayList<String>();
    }
}
