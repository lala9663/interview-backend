package com.interview.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 시각 변환. DB 에는 업무 시간대(app.time-zone, 기본 Asia/Seoul)의 LocalDateTime(TIMESTAMP)으로 저장하고,
 * API 로 나갈 때 오프셋을 붙인다(API 명세: ISO-8601 오프셋 포함).
 */
public final class Times {

    private static volatile ZoneId zone = ZoneId.of("Asia/Seoul");

    private Times() {
    }

    public static void setZone(ZoneId zoneId) {
        zone = zoneId;
    }

    public static ZoneId zone() {
        return zone;
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(zone).truncatedTo(ChronoUnit.MILLIS);
    }

    public static LocalDate today() {
        return LocalDate.now(zone);
    }

    public static OffsetDateTime toOffset(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.truncatedTo(ChronoUnit.SECONDS).atZone(zone).toOffsetDateTime();
    }

    public static LocalDateTime toLocal(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZoneSameInstant(zone).toLocalDateTime();
    }
}
