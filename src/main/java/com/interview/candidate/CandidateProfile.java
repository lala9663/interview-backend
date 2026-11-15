package com.interview.candidate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 명세 2.3 의 profile. 블라인드 항목이 이미 빠진 값만 담는다(성별·생년월일·가족 등은 필드 자체가 없다).
 * IV_CANDIDATE.PROFILE_JSON(CLOB)에 JSON 문자열로 저장된다.
 */
@Data
@NoArgsConstructor
public class CandidateProfile {

    private List<Education> education = new ArrayList<Education>();
    private List<String> courses = new ArrayList<String>();
    private List<TitledText> essays = new ArrayList<TitledText>();
    private List<TitledText> projects = new ArrayList<TitledText>();
    private List<Career> careers = new ArrayList<Career>();
    private List<String> certificates = new ArrayList<String>();
    /** 그 밖의 항목(어학, 수상 등). 키는 excel-mapping.yml 의 extra.* 이름. */
    private Map<String, Object> extra = new LinkedHashMap<String, Object>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Education {
        private String school;
        private String major;
        private String degree;
        private String gpa;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TitledText {
        private String title;
        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Career {
        private String company;
        private String role;
        private String period;
        private String text;
    }
}
