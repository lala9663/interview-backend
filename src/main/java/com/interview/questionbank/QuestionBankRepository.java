package com.interview.questionbank;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.interview.interview.InterviewType;

public interface QuestionBankRepository
        extends JpaRepository<QuestionBankItem, Long>, JpaSpecificationExecutor<QuestionBankItem> {

    /** 그 직무·면접 유형에 쓸 수 있는 질문(공통 포함). */
    @Query("select b from QuestionBankItem b where (b.jobCode = :jobCode or b.jobCode is null) "
            + "and (b.interviewType = :interviewType or b.interviewType is null) order by b.id")
    List<QuestionBankItem> findUsable(@Param("jobCode") String jobCode,
            @Param("interviewType") InterviewType interviewType, Pageable pageable);
}
