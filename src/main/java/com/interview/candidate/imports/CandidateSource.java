package com.interview.candidate.imports;

import java.util.List;

/**
 * 면접자 입력 경로의 교체 지점. 입력(엑셀 파일, API 본문 ...)을 공통 모양({@link CandidateRow})으로 바꾸는 일만 한다.
 * 검증·저장은 모두 {@link CandidateImportService} 가 한다.
 *
 * - 엑셀: com.interview.upload.ExcelCandidateSource (지금 쓰는 경로)
 * - API : {@link ApiCandidateSource} (경력용, 자리만 있음)
 *
 * 새 입력 경로를 붙이는 방법은 .opencode/skills/add-candidate-source/SKILL.md 참고.
 *
 * @param <I> 입력의 타입
 */
public interface CandidateSource<I> {

    /** IV_CANDIDATE.SOURCE_KIND 에 남는 값. 예: EXCEL, API */
    String sourceType();

    /** 입력 전체를 읽을 수 없으면 ApiException 을 던진다. 행 단위 문제는 CandidateRow.errors 에 담는다. */
    List<CandidateRow> read(I input);
}
