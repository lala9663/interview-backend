package com.interview.persona;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interview.common.ApiException;
import com.interview.common.Times;

import lombok.RequiredArgsConstructor;

/**
 * 면접 페르소나의 목록·관리 규칙과 "이 면접에 적용되는 페르소나" 고르기(API 명세 2.12).
 *
 * 규칙:
 * - 추천으로 바꾸면 쓰는 것으로도 바뀐다. 쓰지 않는 것으로 바꾸면 추천도 풀린다.
 * - 쓰는 페르소나가 하나뿐이면 그것을 끄거나 지울 수 없다(PERSONA_LAST_ONE).
 * - 기본 페르소나(custom = false)는 지울 수 없다(PERSONA_BUILT_IN).
 * - 이름은 겹칠 수 없다(PERSONA_NAME_TAKEN).
 */
@Service
@RequiredArgsConstructor
public class PersonaService {

    public static final String DEFAULT_TONE = "neutral";
    private static final List<String> TONES = Arrays.asList("brand", "ok", "warn", "bad", "neutral");

    private final PersonaRepository repository;

    @Transactional(readOnly = true)
    public List<Persona> list() {
        return repository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public Persona get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("PERSONA_NOT_FOUND", "페르소나를 찾을 수 없습니다."));
    }

    /** 면접에 고를 수 있는(쓰는) 페르소나인지 확인하고 돌려준다. */
    @Transactional(readOnly = true)
    public Persona requireAvailable(Long id) {
        Persona persona = get(id);
        if (!persona.isEnabled()) {
            throw ApiException.badRequest("PERSONA_NOT_AVAILABLE", "쓰지 않는 페르소나입니다. 다른 페르소나를 골라 주세요.");
        }
        return persona;
    }

    @Transactional
    public Persona create(PersonaDtos.SaveRequest request) {
        Persona p = new Persona();
        p.setName(requireFreeName(request.getName(), null));
        p.setDescription(text(request.getDescription(), 1000, "설명"));
        p.setQuestionType(text(request.getQuestionType(), 100, "질문 유형"));
        p.setFollowUpType(text(request.getFollowUpType(), 100, "꼬리질문 유형"));
        p.setFit(text(request.getFit(), 500, "맞는 상황"));
        p.setGuard(text(request.getGuard(), 1000, "지킬 것"));
        p.setTone(request.getTone() == null || request.getTone().trim().isEmpty() ? DEFAULT_TONE : tone(request.getTone()));
        p.setEnabled(true);
        p.setRecommended(false);
        p.setCustom(true);
        p.setCreatedAt(Times.now());
        return flushName(p);
    }

    /** 보낸(null 이 아닌) 것만 바꾼다. */
    @Transactional
    public Persona patch(Long id, PersonaDtos.SaveRequest request) {
        Persona p = get(id);
        if (Boolean.TRUE.equals(request.getRecommended()) && Boolean.FALSE.equals(request.getEnabled())) {
            throw ApiException.validation("쓰지 않는 페르소나를 추천으로 둘 수 없습니다.");
        }
        if (request.getName() != null) {
            p.setName(requireFreeName(request.getName(), id));
        }
        if (request.getDescription() != null) {
            p.setDescription(text(request.getDescription(), 1000, "설명"));
        }
        if (request.getQuestionType() != null) {
            p.setQuestionType(text(request.getQuestionType(), 100, "질문 유형"));
        }
        if (request.getFollowUpType() != null) {
            p.setFollowUpType(text(request.getFollowUpType(), 100, "꼬리질문 유형"));
        }
        if (request.getFit() != null) {
            p.setFit(text(request.getFit(), 500, "맞는 상황"));
        }
        if (request.getGuard() != null) {
            p.setGuard(text(request.getGuard(), 1000, "지킬 것"));
        }
        if (request.getTone() != null) {
            p.setTone(tone(request.getTone()));
        }
        if (Boolean.FALSE.equals(request.getEnabled())) {
            if (p.isEnabled()) {
                requireNotLastEnabled("쓰는 페르소나가 하나뿐이라 끌 수 없습니다.");
            }
            p.setEnabled(false);
            p.setRecommended(false);
        }
        if (Boolean.TRUE.equals(request.getEnabled())) {
            p.setEnabled(true);
        }
        if (Boolean.TRUE.equals(request.getRecommended())) {
            p.setRecommended(true);
            p.setEnabled(true);
        }
        if (Boolean.FALSE.equals(request.getRecommended())) {
            p.setRecommended(false);
        }
        return flushName(p);
    }

    /**
     * 바로 DB 에 쓴다. 같은 이름을 동시에 저장해 이름 유니크 제약(이 표의 하나뿐인 유니크 제약)에 걸리면
     * 미리 검사했을 때와 같은 409 PERSONA_NAME_TAKEN 으로 답한다.
     */
    private Persona flushName(Persona p) {
        try {
            return repository.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("PERSONA_NAME_TAKEN", "같은 이름의 페르소나가 이미 있습니다.");
        }
    }

    @Transactional
    public void delete(Long id) {
        Persona p = get(id);
        if (!p.isCustom()) {
            throw ApiException.conflict("PERSONA_BUILT_IN", "기본 페르소나는 지울 수 없습니다. 쓰지 않으려면 꺼 주세요.");
        }
        if (p.isEnabled()) {
            requireNotLastEnabled("쓰는 페르소나가 하나뿐이라 지울 수 없습니다.");
        }
        repository.delete(p);
    }

    /** 지금의 페르소나 목록으로 "면접에 적용되는 페르소나"를 고르는 도우미. 목록을 한 번만 읽어 여러 면접에 쓴다. */
    @Transactional(readOnly = true)
    public Resolver resolver() {
        return new Resolver(repository.findAllByOrderByIdAsc());
    }

    /** 표가 비어 있으면 기본 다섯 가지를 넣는다. 넣었으면 true. */
    @Transactional
    public boolean insertDefaultsIfEmpty() {
        if (repository.count() > 0) {
            return false;
        }
        for (Persona p : PersonaDefaults.create()) {
            repository.save(p);
        }
        return true;
    }

    private void requireNotLastEnabled(String message) {
        if (repository.countByEnabledTrue() <= 1) {
            throw ApiException.conflict("PERSONA_LAST_ONE", message);
        }
    }

    private String requireFreeName(String name, Long selfId) {
        if (name == null || name.trim().isEmpty()) {
            throw ApiException.badRequest("PERSONA_NAME_REQUIRED", "페르소나 이름을 적어 주세요.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 100) {
            throw ApiException.validation("이름은 100자까지 쓸 수 있습니다.");
        }
        for (Persona other : repository.findByName(trimmed)) {
            if (!other.getId().equals(selfId)) {
                throw ApiException.conflict("PERSONA_NAME_TAKEN", "같은 이름의 페르소나가 이미 있습니다.");
            }
        }
        return trimmed;
    }

    /** 앞뒤 공백을 떼고, 비어 있으면 null(Oracle 에서 빈 문자열은 NULL 이다). */
    private static String text(String value, int max, String label) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw ApiException.validation(label + "은(는) " + max + "자까지 쓸 수 있습니다.");
        }
        return trimmed;
    }

    private static String tone(String value) {
        String trimmed = value.trim();
        if (!TONES.contains(trimmed)) {
            throw ApiException.validation("tone 은 " + TONES + " 가운데 하나여야 합니다.");
        }
        return trimmed;
    }

    /**
     * 면접에 적용되는 페르소나를 고른다.
     * - 고른 것이 있고 쓰는 중이면 그것.
     * - 고른 것이 꺼졌으면 기본 선택. 다만 방이 LIVE·ENDED 이면(locked) 꺼졌어도 고른 것 그대로.
     * - 고른 것이 없으면(또는 지워졌으면) 기본 선택.
     * 기본 선택: 추천이면서 쓰는 것 가운데 만든 순서가 가장 앞선 것. 추천이 없으면 쓰는 것 가운데 가장 앞선 것. 없으면 null.
     */
    public static class Resolver {

        private final List<Persona> all;

        Resolver(List<Persona> all) {
            this.all = new ArrayList<Persona>(all);
        }

        public Persona resolve(Long chosenId, boolean locked) {
            if (chosenId != null) {
                for (Persona p : all) {
                    if (p.getId().equals(chosenId) && (p.isEnabled() || locked)) {
                        return p;
                    }
                }
            }
            return defaultChoice();
        }

        public Persona defaultChoice() {
            Persona firstEnabled = null;
            for (Persona p : all) {
                if (!p.isEnabled()) {
                    continue;
                }
                if (p.isRecommended()) {
                    return p;
                }
                if (firstEnabled == null) {
                    firstEnabled = p;
                }
            }
            return firstEnabled;
        }
    }
}
