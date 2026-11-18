package com.interview.systemstatus;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** API 명세 2.13 시스템 상태. 로그인한 사용자 모두. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SystemStatusController {

    private final SystemStatusService systemStatusService;

    @GetMapping("/system/status")
    public SystemStatusDtos.Status status() {
        return systemStatusService.status();
    }
}
