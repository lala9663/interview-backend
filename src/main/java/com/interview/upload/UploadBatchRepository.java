package com.interview.upload;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadBatchRepository extends JpaRepository<UploadBatch, Long> {

    List<UploadBatch> findByRecruitmentIdOrderByIdDesc(Long recruitmentId);
}
