package com.interview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 면접 지원 백엔드. 기능은 com.interview.&lt;feature&gt; 패키지 단위로 나뉜다 (AGENTS.md 참고).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class InterviewBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewBackendApplication.class, args);
    }
}
