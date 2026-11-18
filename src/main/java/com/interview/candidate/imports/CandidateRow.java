package com.interview.candidate.imports;

import java.util.ArrayList;
import java.util.List;

import com.interview.candidate.ApplicantType;
import com.interview.candidate.CandidateProfile;

import lombok.Getter;
import lombok.Setter;

/** 입력 경로와 무관한 "면접자 한 명" 모양. 블라인드 항목의 값은 여기까지 오지 않는다(키만 온다). */
@Getter
@Setter
public class CandidateRow {

    /** 원본에서의 위치(엑셀은 시트의 행 번호, API 는 배열 순번 + 1). 오류 표시에 쓴다. */
    private int rowNumber;
    private String applicantNo;
    private String name;
    private String jobCode;
    private String jobTitle;
    /** 면접자 유형. 입력에 없거나 알 수 없는 값이면 null. */
    private ApplicantType applicantType;
    private CandidateProfile profile = new CandidateProfile();
    /** 입력에 있었지만 버린 블라인드 항목의 키. */
    private List<String> blindedFields = new ArrayList<String>();
    /** 입력 경로가 이미 찾아낸 행 단위 오류(필수 값 누락 등). 비어 있지 않으면 저장하지 않는다. */
    private List<RowError> errors = new ArrayList<RowError>();
}
