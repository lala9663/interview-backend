package com.interview.systemstatus;

/** API 명세의 ServiceStatus. DEGRADED = 느리거나 일부 실패. */
public enum ServiceStatus {
    OK, DEGRADED, DOWN;

    /** Agent 가 준 글자를 상태로 바꾼다. 없거나 모르는 값이면 fallback. */
    public static ServiceStatus parse(String text, ServiceStatus fallback) {
        if (text != null) {
            for (ServiceStatus s : values()) {
                if (s.name().equalsIgnoreCase(text.trim())) {
                    return s;
                }
            }
        }
        return fallback;
    }
}
