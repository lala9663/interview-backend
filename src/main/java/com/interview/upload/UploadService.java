package com.interview.upload;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.interview.auth.CurrentUser;
import com.interview.candidate.imports.CandidateImportService;
import com.interview.candidate.imports.CandidateRow;
import com.interview.candidate.imports.ImportResult;
import com.interview.candidate.imports.RowError;
import com.interview.common.ApiException;
import com.interview.common.Times;
import com.interview.recruitment.RecruitmentService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UploadService {

    private final ExcelCandidateSource excelSource;
    private final CandidateImportService importService;
    private final RecruitmentService recruitmentService;
    private final UploadBatchRepository batchRepository;
    private final UploadErrorRepository errorRepository;

    /**
     * 엑셀을 읽어 면접자를 저장하고 업로드 이력을 남긴다.
     * 파일 자체를 읽지 못하면(FILE_UNREADABLE 등) 이력도 남기지 않고 400 을 돌려준다.
     */
    @Transactional
    public UploadBatchDto upload(Long recruitmentId, MultipartFile file, CurrentUser user) {
        recruitmentService.get(recruitmentId);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("FILE_EMPTY", "파일이 비어 있습니다.");
        }
        List<CandidateRow> rows;
        try (InputStream in = file.getInputStream()) {
            rows = excelSource.read(in);
        } catch (IOException e) {
            throw ApiException.badRequest("FILE_UNREADABLE", "파일을 읽을 수 없습니다.");
        }
        ImportResult result = importService.importRows(recruitmentId, excelSource.sourceType(), rows);

        UploadBatch batch = new UploadBatch();
        batch.setRecruitmentId(recruitmentId);
        batch.setFileName(cut(file.getOriginalFilename(), 300));
        batch.setUploadedAt(Times.now());
        batch.setUploadedById(user.getId());
        batch.setUploadedByName(user.getName());
        batch.setTotalRows(result.getTotalRows());
        batch.setSuccessRows(result.getSuccessRows());
        batch.setFailedRows(result.getFailedRows());
        batchRepository.save(batch);

        List<UploadError> errors = new ArrayList<UploadError>();
        for (RowError re : result.getErrors()) {
            UploadError e = new UploadError();
            e.setBatchId(batch.getId());
            e.setRowNumber(re.getRowNumber());
            e.setColumnName(re.getColumn());
            e.setCellValue(cut(re.getValue(), 500));
            e.setCode(re.getCode());
            e.setMessage(cut(re.getMessage(), 500));
            errors.add(errorRepository.save(e));
        }
        return new UploadBatchDto(batch, errors);
    }

    @Transactional(readOnly = true)
    public List<UploadBatchDto> history(Long recruitmentId) {
        recruitmentService.get(recruitmentId);
        List<UploadBatchDto> items = new ArrayList<UploadBatchDto>();
        for (UploadBatch b : batchRepository.findByRecruitmentIdOrderByIdDesc(recruitmentId)) {
            items.add(new UploadBatchDto(b, null));
        }
        return items;
    }

    @Transactional(readOnly = true)
    public UploadBatchDto detail(Long batchId) {
        UploadBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> ApiException.notFound("UPLOAD_NOT_FOUND", "업로드 이력을 찾을 수 없습니다."));
        return new UploadBatchDto(batch, errorRepository.findByBatchIdOrderByRowNumberAscIdAsc(batchId));
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
