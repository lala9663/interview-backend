package com.interview.systemstatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** API 명세 2.13 의 시스템 상태 응답. */
public final class SystemStatusDtos {

    private SystemStatusDtos() {
    }

    @Getter
    @Setter
    public static class Status {
        private OffsetDateTime checkedAt;
        private List<ServiceItem> services = new ArrayList<ServiceItem>();
        private List<WaitItem> waits = new ArrayList<WaitItem>();
        private List<QueueItem> queue = new ArrayList<QueueItem>();
        private List<DailySession> dailySessions = new ArrayList<DailySession>();
    }

    @Getter
    public static class ServiceItem {
        /** BACKEND | DB | AGENT | STT | LLM */
        private final String key;
        private final String name;
        private final String detail;
        private final ServiceStatus status;
        private final String note;

        public ServiceItem(String key, String name, String detail, ServiceStatus status, String note) {
            this.key = key;
            this.name = name;
            this.detail = detail;
            this.status = status;
            this.note = note;
        }
    }

    @Getter
    public static class WaitItem {
        /** JobType 이름. */
        private final String key;
        private final String label;
        /** 가장 최근 5건의 평균(초). */
        private final long seconds;
        /** 가장 최근 50건의 평균(초). */
        private final long usual;

        public WaitItem(String key, String label, long seconds, long usual) {
            this.key = key;
            this.label = label;
            this.seconds = seconds;
            this.usual = usual;
        }
    }

    @Getter
    public static class QueueItem {
        /** JobType 이름. */
        private final String key;
        private final String label;
        private final long waiting;
        private final long running;

        public QueueItem(String key, String label, long waiting, long running) {
            this.key = key;
            this.label = label;
            this.waiting = waiting;
            this.running = running;
        }
    }

    @Getter
    public static class DailySession {
        private final LocalDate date;
        private final int count;

        public DailySession(LocalDate date, int count) {
            this.date = date;
            this.count = count;
        }
    }
}
