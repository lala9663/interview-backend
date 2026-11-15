package com.interview.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interview.job.AiJob;
import com.interview.room.RoomService;
import com.interview.room.TranscriptService;

import lombok.RequiredArgsConstructor;

/** Agent → 백엔드 콜백(API 명세 5장). 인증은 X-Internal-Key ({@link InternalKeyFilter}). */
@RestController
@RequestMapping("/internal/v1/rooms/{roomId}")
@RequiredArgsConstructor
public class InternalRoomController {

    private final InternalRoomService internalRoomService;
    private final TranscriptService transcriptService;
    private final RoomService roomService;

    @GetMapping("/context")
    public InternalDtos.RoomContext context(@PathVariable Long roomId) {
        return internalRoomService.context(roomId);
    }

    /** 확정 전사 저장(묶음). (roomId, seq)가 같으면 덮어쓴다. */
    @PostMapping("/utterances")
    public Map<String, Object> utterances(@PathVariable Long roomId,
            @Valid @RequestBody InternalDtos.UtterancesRequest request) {
        return saved(transcriptService.saveLive(roomId, request.getItems()));
    }

    @PostMapping("/follow-ups")
    public Map<String, Object> followUps(@PathVariable Long roomId,
            @Valid @RequestBody InternalDtos.FollowUpsRequest request) {
        return saved(roomService.saveFollowUps(roomId, request.getBasedOnSeq(), request.getItems()));
    }

    @PutMapping("/refined-transcript")
    public Map<String, Object> refinedTranscript(@PathVariable Long roomId,
            @Valid @RequestBody InternalDtos.RefinedTranscriptRequest request) {
        AiJob job = internalRoomService.refinedTranscript(roomId, request);
        Map<String, Object> body = saved(request.getItems().size());
        body.put("jobId", job.getId());
        return body;
    }

    @PostMapping("/events")
    public Map<String, Object> events(@PathVariable Long roomId, @Valid @RequestBody InternalDtos.EventRequest request) {
        roomService.recordEvent(roomId, request.getType(), request.getMessage(), request.getData());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("ok", true);
        return body;
    }

    private static Map<String, Object> saved(int count) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("saved", count);
        return body;
    }
}
