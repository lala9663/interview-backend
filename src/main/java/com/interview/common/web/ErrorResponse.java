package com.interview.common.web;

import java.util.Collections;
import java.util.Map;

import lombok.Getter;

/** 계약 1장의 공통 오류 응답. */
@Getter
public class ErrorResponse {

    private final String code;
    private final String message;
    private final Map<String, Object> details;

    public ErrorResponse(String code, String message, Map<String, Object> details) {
        this.code = code;
        this.message = message;
        this.details = details == null ? Collections.<String, Object>emptyMap() : details;
    }

    public ErrorResponse(String code, String message) {
        this(code, message, null);
    }
}
