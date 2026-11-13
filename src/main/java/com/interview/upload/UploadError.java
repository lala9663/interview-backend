package com.interview.upload;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 업로드에서 실패한 행. ROW, COLUMN 은 예약어라 ROW_NO, COLUMN_NAME 을 쓴다. */
@Entity
@Table(name = "IV_UPLOAD_ERROR", indexes = @Index(name = "IX_IV_UPLOAD_ERROR_BATCH", columnList = "BATCH_ID"))
@Getter
@Setter
@NoArgsConstructor
public class UploadError {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivUploadErrorSeq")
    @SequenceGenerator(name = "ivUploadErrorSeq", sequenceName = "IV_UPLOAD_ERROR_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "BATCH_ID", nullable = false)
    private Long batchId;

    @Column(name = "ROW_NO", nullable = false)
    private int rowNumber;

    @Column(name = "COLUMN_NAME", length = 100)
    private String columnName;

    @Column(name = "CELL_VALUE", length = 500)
    private String cellValue;

    @Column(name = "ERROR_CODE", length = 50)
    private String code;

    @Column(name = "ERROR_MESSAGE", length = 500)
    private String message;
}
