package com.interview.candidate.imports;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.interview.candidate.CandidateProfile;

import lombok.Getter;
import lombok.Setter;

/**
 * API 입력 경로의 자리 (경력 채용: 외부 채용 시스템이 면접자를 밀어 넣는 경우).
 *
 * 지금 있는 것: API 본문 한 건({@link Payload})을 {@link CandidateRow} 로 바꾸는 변환.
 * 아직 없는 것: 이 변환을 부르는 엔드포인트. 계약(docs/contract/api-contract.md)에 경로가 정해지면
 *   1) 계약 변경 절차를 밟고(.opencode/skills/change-api-contract),
 *   2) 컨트롤러에서 read() → CandidateImportService.importRows(recruitmentId, sourceType(), rows) 를 부른다.
 *
 * 블라인드: Payload 에는 블라인드 항목 필드를 두지 않는다. 보내는 쪽이 넣어 보내도 역직렬화에서 버려진다.
 * 무엇이 버려졌는지는 blindedFields 로 보내는 쪽이 알려 주게 한다.
 */
@Component
public class ApiCandidateSource implements CandidateSource<List<ApiCandidateSource.Payload>> {

    @Override
    public String sourceType() {
        return "API";
    }

    @Override
    public List<CandidateRow> read(List<Payload> input) {
        List<CandidateRow> rows = new ArrayList<CandidateRow>();
        int index = 0;
        for (Payload p : input) {
            index++;
            CandidateRow row = new CandidateRow();
            row.setRowNumber(index);
            row.setApplicantNo(trim(p.getApplicantNo()));
            row.setName(trim(p.getName()));
            row.setJobCode(trim(p.getJobCode()));
            row.setJobTitle(trim(p.getJobTitle()));
            row.setProfile(p.getProfile() == null ? new CandidateProfile() : p.getProfile());
            if (p.getBlindedFields() != null) {
                row.setBlindedFields(p.getBlindedFields());
            }
            require(row, "applicantNo", row.getApplicantNo());
            require(row, "name", row.getName());
            require(row, "jobCode", row.getJobCode());
            rows.add(row);
        }
        return rows;
    }

    private static void require(CandidateRow row, String field, String value) {
        if (value == null || value.isEmpty()) {
            row.getErrors().add(new RowError(row.getRowNumber(), field, null, RowError.REQUIRED_MISSING,
                    "필수 값이 비어 있습니다."));
        }
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    @Getter
    @Setter
    public static class Payload {
        private String applicantNo;
        private String name;
        private String jobCode;
        private String jobTitle;
        private CandidateProfile profile;
        private List<String> blindedFields;
    }
}
