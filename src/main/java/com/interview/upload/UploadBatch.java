package com.interview.upload;

import java.time.LocalDateTime;

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

/** 업로드 한 번. */
@Entity
@Table(name = "IV_UPLOAD_BATCH", indexes = @Index(name = "IX_IV_UPLOAD_BATCH_RECR", columnList = "RECRUITMENT_ID"))
@Getter
@Setter
@NoArgsConstructor
public class UploadBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ivUploadBatchSeq")
    @SequenceGenerator(name = "ivUploadBatchSeq", sequenceName = "IV_UPLOAD_BATCH_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "RECRUITMENT_ID", nullable = false)
    private Long recruitmentId;

    @Column(name = "FILE_NAME", length = 300)
    private String fileName;

    @Column(name = "UPLOADED_AT", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "UPLOADED_BY_ID")
    private Long uploadedById;

    @Column(name = "UPLOADED_BY_NAME", length = 100)
    private String uploadedByName;

    @Column(name = "TOTAL_ROWS", nullable = false)
    private int totalRows;

    @Column(name = "SUCCESS_ROWS", nullable = false)
    private int successRows;

    @Column(name = "FAILED_ROWS", nullable = false)
    private int failedRows;
}
