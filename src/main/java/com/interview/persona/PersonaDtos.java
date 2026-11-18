package com.interview.persona;

import lombok.Getter;
import lombok.Setter;

/** API 명세 2.12 의 페르소나 요청·응답. */
public final class PersonaDtos {

    private PersonaDtos() {
    }

    /** Persona 전체 모양. */
    @Getter
    public static class PersonaDto {
        private final Long id;
        private final String name;
        private final String description;
        private final String questionType;
        private final String followUpType;
        private final String fit;
        private final String guard;
        private final String tone;
        private final boolean recommended;
        private final boolean enabled;
        private final boolean custom;

        public PersonaDto(Persona p) {
            this.id = p.getId();
            this.name = p.getName();
            this.description = p.getDescription();
            this.questionType = p.getQuestionType();
            this.followUpType = p.getFollowUpType();
            this.fit = p.getFit();
            this.guard = p.getGuard();
            this.tone = p.getTone() == null ? PersonaService.DEFAULT_TONE : p.getTone();
            this.recommended = p.isRecommended();
            this.enabled = p.isEnabled();
            this.custom = p.isCustom();
        }
    }

    /** Interview 응답에 싣는 요약(API 명세 2.5). */
    @Getter
    public static class Summary {
        private final Long id;
        private final String name;
        private final String tone;
        private final String questionType;
        private final String followUpType;

        public Summary(Persona p) {
            this.id = p.getId();
            this.name = p.getName();
            this.tone = p.getTone() == null ? PersonaService.DEFAULT_TONE : p.getTone();
            this.questionType = p.getQuestionType();
            this.followUpType = p.getFollowUpType();
        }
    }

    /** POST /admin/personas, PATCH /admin/personas/{id} 의 본문. PATCH 는 보낸(null 이 아닌) 것만 바꾼다. */
    @Getter
    @Setter
    public static class SaveRequest {
        private String name;
        private String description;
        private String questionType;
        private String followUpType;
        private String fit;
        private String guard;
        private String tone;
        /** PATCH 에서만 쓴다. */
        private Boolean recommended;
        /** PATCH 에서만 쓴다. */
        private Boolean enabled;
    }
}
