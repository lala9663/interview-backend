package com.interview.room;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.interview.auth.CurrentUser;
import com.interview.common.web.ListResponse;
import com.interview.question.QuestionDto;

import lombok.RequiredArgsConstructor;

/** 계약 2.7 면접 방. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping("/interviews/{id}/room")
    public RoomDtos.RoomDto createOrGet(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        return roomService.createOrGet(id, user);
    }

    @GetMapping("/rooms/{id}")
    public RoomDtos.RoomDto get(@PathVariable Long id) {
        return roomService.get(id);
    }

    @PostMapping("/rooms/{id}/start")
    public RoomDtos.RoomDto start(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        return roomService.start(id, user);
    }

    @PostMapping("/rooms/{id}/end")
    public RoomDtos.RoomDto end(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        return roomService.end(id, user);
    }

    @PostMapping("/rooms/{id}/ticket")
    public RoomDtos.Ticket ticket(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal CurrentUser user) {
        boolean audio = body != null && Boolean.TRUE.equals(body.get("audio"));
        return roomService.ticket(id, audio, user);
    }

    @GetMapping("/rooms/{id}/utterances")
    public ListResponse<RoomDtos.UtteranceDto> utterances(@PathVariable Long id,
            @RequestParam(required = false) Integer afterSeq) {
        return ListResponse.of(roomService.utterances(id, afterSeq));
    }

    @GetMapping("/rooms/{id}/follow-ups")
    public ListResponse<RoomDtos.FollowUpDto> followUps(@PathVariable Long id) {
        return ListResponse.of(roomService.followUps(id));
    }

    @PostMapping("/follow-ups/{id}/use")
    public QuestionDto useFollowUp(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        return QuestionDto.from(roomService.useFollowUp(id, user));
    }
}
