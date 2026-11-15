package com.interview.matching;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** API 명세 2.4 의 자동 매칭. (수동 지정 PUT /interviews/{id}/interviewers 는 interview 패키지에 있다.) */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AssignmentController {

    private final AutoAssignmentService autoAssignmentService;

    @PostMapping("/recruitments/{id}/assignments/auto")
    @PreAuthorize("hasRole('ADMIN')")
    public AutoAssignResult auto(@PathVariable Long id) {
        return autoAssignmentService.run(id);
    }
}
