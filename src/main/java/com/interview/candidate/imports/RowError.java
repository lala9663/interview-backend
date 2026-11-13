package com.interview.candidate.imports;

import lombok.Getter;

/** 행 단위 오류. 코드 목록은 docs/excel-upload.md. */
@Getter
public class RowError {

    public static final String REQUIRED_MISSING = "REQUIRED_MISSING";
    public static final String UNKNOWN_JOB_CODE = "UNKNOWN_JOB_CODE";
    public static final String DUPLICATE_IN_FILE = "DUPLICATE_IN_FILE";
    public static final String VALUE_TOO_LONG = "VALUE_TOO_LONG";

    private final int rowNumber;
    private final String column;
    private final String value;
    private final String code;
    private final String message;

    public RowError(int rowNumber, String column, String value, String code, String message) {
        this.rowNumber = rowNumber;
        this.column = column;
        this.value = value;
        this.code = code;
        this.message = message;
    }
}
