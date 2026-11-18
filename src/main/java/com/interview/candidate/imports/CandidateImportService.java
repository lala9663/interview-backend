package com.interview.candidate.imports;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interview.candidate.Candidate;
import com.interview.candidate.CandidateRepository;
import com.interview.common.Times;
import com.interview.recruitment.JobPosting;
import com.interview.recruitment.Recruitment;
import com.interview.recruitment.RecruitmentService;

import lombok.RequiredArgsConstructor;

/**
 * 모든 입력 경로(엑셀, API)가 거치는 면접자 저장 로직.
 * 행 단위로 검증하고, 통과한 행만 저장한다. 실패한 행은 결과의 errors 로 돌려준다.
 *
 * 규칙:
 * - 입력 경로가 이미 오류를 단 행은 저장하지 않는다.
 * - 직무 코드는 그 채용에 등록된 것이어야 한다(UNKNOWN_JOB_CODE).
 * - 같은 입력 안에서 수험번호가 겹치면 뒤의 행이 오류다(DUPLICATE_IN_FILE).
 * - 같은 채용에 이미 있는 수험번호면 새로 만들지 않고 갱신한다.
 * - 면접자 유형은 입력에 값이 있을 때만 넣는다(없으면 새 면접자는 null, 기존 면접자는 그대로).
 */
@Service
@RequiredArgsConstructor
public class CandidateImportService {

    private final CandidateRepository candidateRepository;
    private final RecruitmentService recruitmentService;

    @Transactional
    public ImportResult importRows(Long recruitmentId, String sourceType, List<CandidateRow> rows) {
        Recruitment recruitment = recruitmentService.get(recruitmentId);
        Map<String, JobPosting> jobs = new HashMap<String, JobPosting>();
        for (JobPosting j : recruitmentService.jobs(recruitmentId)) {
            jobs.put(j.getJobCode(), j);
        }

        ImportResult result = new ImportResult();
        result.setTotalRows(rows.size());
        Set<String> seen = new HashSet<String>();
        for (CandidateRow row : rows) {
            if (!row.getErrors().isEmpty()) {
                result.getErrors().addAll(row.getErrors());
                result.setFailedRows(result.getFailedRows() + 1);
                continue;
            }
            JobPosting job = jobs.get(row.getJobCode());
            if (job == null) {
                result.getErrors().add(new RowError(row.getRowNumber(), "지원직무코드", row.getJobCode(),
                        RowError.UNKNOWN_JOB_CODE, "등록되지 않은 직무 코드입니다."));
                result.setFailedRows(result.getFailedRows() + 1);
                continue;
            }
            if (!seen.add(row.getApplicantNo())) {
                result.getErrors().add(new RowError(row.getRowNumber(), "수험번호", row.getApplicantNo(),
                        RowError.DUPLICATE_IN_FILE, "같은 파일 안에 수험번호가 중복됩니다."));
                result.setFailedRows(result.getFailedRows() + 1);
                continue;
            }

            Candidate candidate = candidateRepository
                    .findByRecruitmentIdAndApplicantNo(recruitmentId, row.getApplicantNo()).orElse(null);
            if (candidate == null) {
                candidate = new Candidate();
                candidate.setRecruitmentId(recruitmentId);
                candidate.setApplicantNo(row.getApplicantNo());
                candidate.setCreatedAt(Times.now());
                result.setCreatedCount(result.getCreatedCount() + 1);
            } else {
                result.setUpdatedCount(result.getUpdatedCount() + 1);
            }
            candidate.setName(row.getName());
            candidate.setTrack(recruitment.getTrack());
            candidate.setJobCode(job.getJobCode());
            // 직무명은 등록된 JD 의 제목을 따른다(파일의 직무명이 달라도 코드가 기준).
            candidate.setJobTitle(job.getTitle());
            // 유형은 입력에 값이 있을 때만 바꾼다(유형 열이 없는 파일을 다시 올려도 이미 넣은 유형이 지워지지 않게).
            if (row.getApplicantType() != null) {
                candidate.setApplicantType(row.getApplicantType());
            }
            candidate.setProfile(row.getProfile());
            candidate.setBlindedFields(row.getBlindedFields());
            candidate.setSourceType(sourceType);
            candidate.setUpdatedAt(Times.now());
            candidateRepository.save(candidate);
            result.setSuccessRows(result.getSuccessRows() + 1);
        }
        return result;
    }
}
