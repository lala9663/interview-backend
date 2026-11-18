package com.interview.persona;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 서버가 뜰 때 페르소나 표가 비어 있으면 기본 다섯 가지를 넣는다(모든 프로파일).
 * 서버 여러 대가 동시에 뜨면 둘 다 넣으려 할 수 있다. 그때는 이름 유니크 제약에 걸린 쪽이 넣지 않고 넘어간다.
 */
@Component
@Order(5)
@RequiredArgsConstructor
public class PersonaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PersonaInitializer.class);

    private final PersonaService personaService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (personaService.insertDefaultsIfEmpty()) {
                log.info("기본 면접 페르소나 다섯 가지를 넣었습니다.");
            }
        } catch (DataIntegrityViolationException e) {
            log.info("기본 면접 페르소나는 다른 서버가 이미 넣었습니다.");
        }
    }
}
