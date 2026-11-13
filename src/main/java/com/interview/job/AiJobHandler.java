package com.interview.job;

/**
 * 작업 유형별 실행기. 기능 패키지(briefing, evaluation, report)가 하나씩 구현하고 @Component 로 등록한다.
 * 실행기는 AiJobRunner 가 스레드풀(aiJobExecutor)에서 부른다.
 */
public interface AiJobHandler {

    JobType type();

    /**
     * 작업을 실행한다. 예외를 던지면 작업은 FAILED 가 된다.
     * Agent 호출은 트랜잭션 밖에서 하고(오래 걸린다), 결과 저장만 트랜잭션으로 묶는다.
     */
    void handle(AiJob job) throws Exception;

    /** 작업이 끝난 뒤(성공·실패 모두) 불린다. 서버 재시작으로 실패 처리될 때도 success=false 로 불린다. */
    default void onFinished(AiJob job, boolean success) {
    }
}
