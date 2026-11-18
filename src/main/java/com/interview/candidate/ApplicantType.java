package com.interview.candidate;

/**
 * 면접자 유형(API 명세의 ApplicantType). 모르면 null 로 둔다.
 * 신입: SCHOLARSHIP, OPEN, INTERN. 경력: CAREER, PHD, CAREER_MASTER.
 */
public enum ApplicantType {
    SCHOLARSHIP("장학생"), OPEN("공채"), INTERN("인턴 전환"), CAREER("일반 경력"), PHD("박사"), CAREER_MASTER("경력 석사");

    private final String label;

    ApplicantType(String label) {
        this.label = label;
    }

    /** 한국어 이름(엑셀의 "지원유형" 열에 쓰는 글자). */
    public String getLabel() {
        return label;
    }

    /**
     * 엑셀 등에서 온 글자를 유형으로 바꾼다. 한국어 이름(공백 무시)이나 enum 이름(대소문자 무시)을 받는다.
     * 비어 있거나 알 수 없는 값이면 null.
     */
    public static ApplicantType parse(String text) {
        if (text == null) {
            return null;
        }
        String key = text.replaceAll("\\s+", "");
        if (key.isEmpty()) {
            return null;
        }
        for (ApplicantType t : values()) {
            if (t.name().equalsIgnoreCase(key) || t.label.replaceAll("\\s+", "").equals(key)) {
                return t;
            }
        }
        return null;
    }
}
