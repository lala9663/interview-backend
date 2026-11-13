package com.interview.user;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.interview.common.web.ListResponse;

import lombok.RequiredArgsConstructor;

/** GET /api/v1/interviewers?jobCode= */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InterviewerController {

    private final UserService userService;

    @GetMapping("/interviewers")
    public ListResponse<UserDto> interviewers(@RequestParam(required = false) String jobCode) {
        List<UserDto> items = new ArrayList<UserDto>();
        for (UserAccount u : userService.findInterviewers(jobCode)) {
            items.add(UserDto.from(u));
        }
        return ListResponse.of(items);
    }
}
