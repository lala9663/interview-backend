package com.interview.common;

import java.time.ZoneId;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** app.time-zone 설정을 {@link Times} 에 반영한다. */
@Configuration
public class TimeZoneConfig {

    @Value("${app.time-zone:Asia/Seoul}")
    private String timeZone;

    @PostConstruct
    public void apply() {
        Times.setZone(ZoneId.of(timeZone));
    }
}
