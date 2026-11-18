package com.interview.interview;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.interview.persona.Persona;
import com.interview.persona.PersonaService;
import com.interview.room.Room;
import com.interview.room.RoomRepository;
import com.interview.room.RoomStatus;

import lombok.RequiredArgsConstructor;

/** 면접 한 건에 적용되는 페르소나(API 명세 2.12)를 고른다. 고르는 규칙은 {@link PersonaService.Resolver} 에 있다. */
@Component
@RequiredArgsConstructor
public class InterviewPersonas {

    private final RoomRepository roomRepository;
    private final PersonaService personaService;

    /** 방이 LIVE·ENDED 이면 페르소나를 바꿀 수 없고, 고른 것이 꺼져도 그대로 보여 준다. */
    public static boolean locks(RoomStatus roomStatus) {
        return roomStatus == RoomStatus.LIVE || roomStatus == RoomStatus.ENDED;
    }

    @Transactional(readOnly = true)
    public boolean isLocked(Long interviewId) {
        Room room = roomRepository.findByInterviewId(interviewId).orElse(null);
        return room != null && locks(room.getStatus());
    }

    /** 이 면접에 적용되는 페르소나. 쓰는 페르소나가 하나도 없으면 null. */
    @Transactional(readOnly = true)
    public Persona effective(Interview interview) {
        return personaService.resolver().resolve(interview.getPersonaId(), isLocked(interview.getId()));
    }
}
