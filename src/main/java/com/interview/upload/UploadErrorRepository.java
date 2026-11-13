package com.interview.upload;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadErrorRepository extends JpaRepository<UploadError, Long> {

    List<UploadError> findByBatchIdOrderByRowNumberAscIdAsc(Long batchId);
}
