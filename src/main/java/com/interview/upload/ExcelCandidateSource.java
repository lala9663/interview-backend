package com.interview.upload;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.interview.candidate.CandidateProfile;
import com.interview.candidate.imports.CandidateRow;
import com.interview.candidate.imports.CandidateSource;
import com.interview.candidate.imports.RowError;
import com.interview.common.ApiException;

import lombok.RequiredArgsConstructor;

/**
 * 엑셀 입력 경로. 매핑(excel-mapping.yml)에 따라 시트의 행을 {@link CandidateRow} 로 바꾼다.
 * 블라인드 컬럼의 셀은 읽지 않는다. 매핑에 없는 컬럼도 읽지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ExcelCandidateSource implements CandidateSource<InputStream> {

    private final ExcelMappingLoader mappingLoader;

    @Value("${app.upload.max-rows:5000}")
    private int maxRows;

    @Override
    public String sourceType() {
        return "EXCEL";
    }

    @Override
    public List<CandidateRow> read(InputStream input) {
        ExcelMapping mapping = mappingLoader.get();
        Workbook workbook;
        try {
            workbook = WorkbookFactory.create(input);
        } catch (Exception e) {
            // DRM(문서 보안)이 걸린 파일, 암호가 걸린 파일, 엑셀이 아닌 파일이 모두 여기로 온다.
            throw ApiException.badRequest("FILE_UNREADABLE",
                    "파일을 열 수 없습니다. 엑셀(xlsx) 파일인지, DRM(문서 보안)이나 암호가 걸려 있지 않은지 확인해 주세요. "
                            + "DRM 이 걸린 파일은 해제한 뒤 다시 올려야 합니다.");
        }
        try {
            if (workbook.getNumberOfSheets() <= mapping.getSheetIndex()) {
                throw ApiException.badRequest("FILE_EMPTY", "읽을 시트가 없습니다.");
            }
            Sheet sheet = workbook.getSheetAt(mapping.getSheetIndex());
            DataFormatter formatter = new DataFormatter();
            int headerIndex = mapping.getHeaderRow() - 1;
            Row headerRow = sheet.getRow(headerIndex);
            if (headerRow == null) {
                throw ApiException.badRequest("FILE_EMPTY", "헤더 행이 비어 있습니다.");
            }

            // 열 번호 → 컬럼 정의. 매핑에 없는 헤더는 넣지 않는다(= 읽지 않는다).
            Map<Integer, ExcelMapping.Column> columns = new LinkedHashMap<Integer, ExcelMapping.Column>();
            List<String> blinded = new ArrayList<String>();
            for (Cell cell : headerRow) {
                ExcelMapping.Column def = mapping.find(formatter.formatCellValue(cell));
                if (def == null) {
                    continue;
                }
                if (def.isBlind()) {
                    if (!blinded.contains(def.getBlindKey())) {
                        blinded.add(def.getBlindKey());
                    }
                } else {
                    columns.put(cell.getColumnIndex(), def);
                }
            }
            List<String> missing = new ArrayList<String>();
            for (ExcelMapping.Column def : mapping.getColumns()) {
                if (def.isRequired() && !columns.containsValue(def)) {
                    missing.add(def.getHeader());
                }
            }
            if (!missing.isEmpty()) {
                Map<String, Object> details = new LinkedHashMap<String, Object>();
                details.put("missingHeaders", missing);
                throw new ApiException(HttpStatus.BAD_REQUEST, "HEADER_MISSING",
                        "필수 컬럼이 없습니다: " + missing, details);
            }

            List<CandidateRow> rows = new ArrayList<CandidateRow>();
            for (int r = headerIndex + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<ExcelMapping.Column, String> values = new LinkedHashMap<ExcelMapping.Column, String>();
                boolean anyValue = false;
                for (Map.Entry<Integer, ExcelMapping.Column> e : columns.entrySet()) {
                    Cell cell = row.getCell(e.getKey());
                    String text = cell == null ? "" : formatter.formatCellValue(cell).trim();
                    values.put(e.getValue(), text);
                    if (!text.isEmpty()) {
                        anyValue = true;
                    }
                }
                if (!anyValue) {
                    continue; // 빈 행
                }
                rows.add(toRow(r + 1, values, blinded));
                if (rows.size() > maxRows) {
                    throw ApiException.badRequest("FILE_TOO_MANY_ROWS", "한 번에 올릴 수 있는 행은 " + maxRows + "개까지입니다.");
                }
            }
            if (rows.isEmpty()) {
                throw ApiException.badRequest("FILE_EMPTY", "면접자 행이 없습니다.");
            }
            return rows;
        } finally {
            try {
                workbook.close();
            } catch (Exception ignore) {
                // 닫기 실패는 무시한다.
            }
        }
    }

    private CandidateRow toRow(int rowNumber, Map<ExcelMapping.Column, String> values, List<String> blinded) {
        CandidateRow row = new CandidateRow();
        row.setRowNumber(rowNumber);
        row.setBlindedFields(new ArrayList<String>(blinded));
        CandidateProfile profile = row.getProfile();
        CandidateProfile.Education education = new CandidateProfile.Education();
        boolean hasEducation = false;

        for (Map.Entry<ExcelMapping.Column, String> e : values.entrySet()) {
            ExcelMapping.Column def = e.getKey();
            String value = e.getValue();
            if (value.isEmpty()) {
                if (def.isRequired()) {
                    row.getErrors().add(new RowError(rowNumber, def.getHeader(), "", RowError.REQUIRED_MISSING,
                            "필수 값이 비어 있습니다."));
                }
                continue;
            }
            if (def.getMaxLength() != null && value.length() > def.getMaxLength()) {
                row.getErrors().add(new RowError(rowNumber, def.getHeader(), value, RowError.VALUE_TOO_LONG,
                        "값이 너무 깁니다(최대 " + def.getMaxLength() + "자)."));
                continue;
            }
            String field = def.getField();
            if ("applicantNo".equals(field)) {
                row.setApplicantNo(value);
            } else if ("name".equals(field)) {
                row.setName(value);
            } else if ("jobCode".equals(field)) {
                row.setJobCode(value);
            } else if ("jobTitle".equals(field)) {
                row.setJobTitle(value);
            } else if ("education.degree".equals(field)) {
                education.setDegree(value);
                hasEducation = true;
            } else if ("education.school".equals(field)) {
                education.setSchool(value);
                hasEducation = true;
            } else if ("education.major".equals(field)) {
                education.setMajor(value);
                hasEducation = true;
            } else if ("education.gpa".equals(field)) {
                education.setGpa(value);
                hasEducation = true;
            } else if ("courses".equals(field)) {
                profile.getCourses().addAll(splitList(value));
            } else if ("certificates".equals(field)) {
                profile.getCertificates().addAll(splitList(value));
            } else if ("essay".equals(field)) {
                profile.getEssays().add(new CandidateProfile.TitledText(titleOf(def), value));
            } else if ("project".equals(field)) {
                profile.getProjects().add(new CandidateProfile.TitledText(titleOf(def), value));
            } else if ("career".equals(field)) {
                profile.getCareers().add(new CandidateProfile.Career(null, null, null, value));
            } else if (field.startsWith("extra.")) {
                profile.getExtra().put(field.substring("extra.".length()), value);
            } else {
                throw new IllegalStateException("엑셀 매핑: 알 수 없는 field 입니다: " + field);
            }
        }
        if (hasEducation) {
            profile.getEducation().add(education);
        }
        return row;
    }

    private static String titleOf(ExcelMapping.Column def) {
        return def.getTitle() == null ? def.getHeader() : def.getTitle();
    }

    private static List<String> splitList(String value) {
        List<String> result = new ArrayList<String>();
        for (String part : Arrays.asList(value.split("[,;\\n]"))) {
            if (!part.trim().isEmpty()) {
                result.add(part.trim());
            }
        }
        return result;
    }
}
