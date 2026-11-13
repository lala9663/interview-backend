package com.interview.room;

/** 화자를 정한 방법. MANUAL = 면접관이 지정, QUESTION_MATCH = 질문 유사도, RULE = 말투·문형 규칙, LLM = 문맥 판정, CHANNEL = 마이크·트랙 분리. */
public enum SpeakerMethod {
    MANUAL, QUESTION_MATCH, RULE, LLM, CHANNEL, NONE
}
