package com.interview.upload;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.interview.common.Times;

import lombok.Getter;

/** API 명세 2.3 의 UploadBatch. 이력 목록에서는 errors 를 비워서 보낸다(상세에서 채운다). */
@Getter
public class UploadBatchDto {

    private final Long id;
    private final String fileName;
    private final OffsetDateTime uploadedAt;
    private final String uploadedBy;
    private final int totalRows;
    private final int successRows;
    private final int failedRows;
    private final List<ErrorItem> errors = new ArrayList<ErrorItem>();

    public UploadBatchDto(UploadBatch batch, List<UploadError> errors) {
        this.id = batch.getId();
        this.fileName = batch.getFileName();
        this.uploadedAt = Times.toOffset(batch.getUploadedAt());
        this.uploadedBy = batch.getUploadedByName();
        this.totalRows = batch.getTotalRows();
        this.successRows = batch.getSuccessRows();
        this.failedRows = batch.getFailedRows();
        if (errors != null) {
            for (UploadError e : errors) {
                this.errors.add(new ErrorItem(e));
            }
        }
    }

    @Getter
    public static class ErrorItem {
        private final int rowNumber;
        private final String column;
        private final String value;
        private final String code;
        private final String message;

        ErrorItem(UploadError e) {
            this.rowNumber = e.getRowNumber();
            this.column = e.getColumnName();
            this.value = e.getCellValue();
            this.code = e.getCode();
            this.message = e.getMessage();
        }
    }
}
