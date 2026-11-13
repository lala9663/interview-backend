package com.interview.job;

import java.net.InetAddress;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** AI 작업 전용 스레드풀. */
@Configuration
@EnableAsync
public class JobConfig {

    public static final String EXECUTOR = "aiJobExecutor";

    @Bean(name = EXECUTOR)
    public ThreadPoolTaskExecutor aiJobExecutor(JobProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getCorePoolSize());
        executor.setMaxPoolSize(props.getMaxPoolSize());
        executor.setQueueCapacity(props.getQueueCapacity());
        executor.setThreadNamePrefix("ai-job-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    /** 이 서버의 작업자 이름. */
    @Bean
    public WorkerId workerId(JobProperties props) {
        String id = props.getInstanceId();
        if (id == null || id.trim().isEmpty()) {
            try {
                id = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                id = "unknown-host";
            }
        }
        return new WorkerId(id.length() > 100 ? id.substring(0, 100) : id);
    }

    public static class WorkerId {
        private final String value;

        public WorkerId(String value) {
            this.value = value;
        }

        public String get() {
            return value;
        }
    }
}
