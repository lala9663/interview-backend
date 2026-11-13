package com.interview.questionbank;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import com.interview.interview.InterviewType;

import lombok.Getter;
import lombok.Setter;

/** 질문 은행 응답 겸 등록 요청({ jobCode, interviewType, category, text, sampleAnswer? }). */
@Getter
@Setter
public class QuestionBankDto {

    private Long id;

    @Size(max = 30)
    private String jobCode;

    private InterviewType interviewType;

    @Size(max = 100)
    private String category;

    @NotBlank(message = "질문 내용은 필수입니다.")
    @Size(max = 1000)
    private String text;

    private String sampleAnswer;

    public static QuestionBankDto from(QuestionBankItem item) {
        QuestionBankDto dto = new QuestionBankDto();
        dto.id = item.getId();
        dto.jobCode = item.getJobCode();
        dto.interviewType = item.getInterviewType();
        dto.category = item.getCategory();
        dto.text = item.getText();
        dto.sampleAnswer = item.getSampleAnswer();
        return dto;
    }
}
