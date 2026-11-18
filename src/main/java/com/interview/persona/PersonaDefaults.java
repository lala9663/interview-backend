package com.interview.persona;

import java.util.ArrayList;
import java.util.List;

import com.interview.common.Times;

/**
 * 기본 페르소나 다섯 가지(API 명세 2.12). 표가 비어 있을 때 이 순서로 넣는다.
 * 글은 프런트엔드 모의 데이터(src/mocks/data.js 의 personas)와 같다. 앞의 셋이 추천이다.
 */
final class PersonaDefaults {

    private PersonaDefaults() {
    }

    static List<Persona> create() {
        List<Persona> list = new ArrayList<Persona>();
        list.add(persona("기술 심층형", "brand", "구조와 원리, 대안 사이의 장단점을 단계별로 파고듭니다.", "원리·대안 질문", "장단점 심화",
                "설계 판단과 기술 이해를 깊게 볼 때", "정답을 유도하지 않고 지원자가 실제로 한 선택을 묻습니다.", true));
        list.add(persona("압박 검증형", "bad", "서류의 주장에 반론을 걸고, 수치와 근거가 나올 때까지 같은 지점을 다시 묻습니다.", "반박형 질문", "근거 추궁",
                "성과 주장이 크고 검증되지 않은 항목이 많을 때", "압박은 주장과 근거에만 겁니다. 인격·감정을 자극하는 표현은 만들지 않습니다.", true));
        list.add(persona("경험 행동형", "ok", "상황, 과제, 행동, 결과 순서로 실제 사례를 끌어냅니다.", "행동 사례 질문", "빠진 단계 보완",
                "본인 역할과 실행의 구체성을 확인할 때", "가정이 아니라 실제로 있었던 일만 묻습니다.", true));
        list.add(persona("표준 구조화형", "neutral", "모든 지원자에게 같은 질문을 같은 순서로 묻고, 빠진 근거만 짧게 확인합니다.", "구조화 질문", "근거 확인",
                "여러 지원자를 같은 기준으로 비교할 때", "준비한 질문 표현을 그대로 씁니다.", false));
        list.add(persona("편안한 대화형", "warn", "열린 질문으로 시작해 지원자가 스스로 이야기를 이어 가도록 돕습니다.", "열린 질문", "이어 말하기 유도",
                "긴장도가 높은 지원자를 만날 때", "칭찬이나 평가로 들릴 수 있는 표현은 최소로 씁니다.", false));
        return list;
    }

    private static Persona persona(String name, String tone, String description, String questionType, String followUpType,
            String fit, String guard, boolean recommended) {
        Persona p = new Persona();
        p.setName(name);
        p.setTone(tone);
        p.setDescription(description);
        p.setQuestionType(questionType);
        p.setFollowUpType(followUpType);
        p.setFit(fit);
        p.setGuard(guard);
        p.setRecommended(recommended);
        p.setEnabled(true);
        p.setCustom(false);
        p.setCreatedAt(Times.now());
        return p;
    }
}
