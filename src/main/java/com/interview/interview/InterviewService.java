package com.interview.interview;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interview.auth.CurrentUser;
import com.interview.briefing.BriefingService;
import com.interview.candidate.Candidate;
import com.interview.candidate.CandidateRepository;
import com.interview.common.ApiException;
import com.interview.common.Times;
import com.interview.common.web.PageResponse;
import com.interview.persona.Persona;
import com.interview.persona.PersonaService;
import com.interview.recruitment.Track;
import com.interview.user.UserAccount;
import com.interview.user.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final InterviewAssigneeRepository assigneeRepository;
    private final CandidateRepository candidateRepository;
    private final InterviewAssembler assembler;
    private final InterviewAccess access;
    private final UserService userService;
    private final PersonaService personaService;
    private final InterviewPersonas interviewPersonas;
    private final BriefingService briefingService;

    public PageResponse<InterviewDto> list(final boolean mine, final LocalDate date, final InterviewStatus status,
            final Long recruitmentId, Integer page, Integer size, final CurrentUser user) {
        Specification<Interview> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<Predicate>();
            if (mine) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<InterviewAssignee> a = sub.from(InterviewAssignee.class);
                sub.select(a.<Long>get("interviewId")).where(cb.equal(a.get("userId"), user.getId()));
                predicates.add(root.get("id").in(sub));
            }
            if (date != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), date.atStartOfDay()));
                predicates.add(cb.lessThan(root.get("scheduledAt"), date.plusDays(1).atStartOfDay()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (recruitmentId != null) {
                predicates.add(cb.equal(root.get("recruitmentId"), recruitmentId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        int p = PageResponse.normalizePage(page);
        int s = PageResponse.normalizeSize(size);
        Page<Interview> result = interviewRepository.findAll(spec,
                PageRequest.of(p, s, Sort.by(Sort.Order.asc("scheduledAt"), Sort.Order.asc("id"))));
        return new PageResponse<InterviewDto>(assembler.toDtos(result.getContent()), p, s, result.getTotalElements());
    }

    public InterviewDto detail(Long id) {
        return assembler.toDto(access.requireExisting(id));
    }

    @Transactional
    public InterviewDto create(Long candidateId, InterviewRequests.Create request) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> ApiException.notFound("CANDIDATE_NOT_FOUND", "면접자를 찾을 수 없습니다."));
        List<Interview> existing = interviewRepository.findByCandidateIdOrderBySeqAsc(candidateId);
        int maxSeq = 0;
        for (Interview i : existing) {
            maxSeq = Math.max(maxSeq, i.getSeq());
            if (i.getType() == request.getType() && i.getStatus() != InterviewStatus.CANCELED) {
                throw ApiException.conflict("INTERVIEW_ALREADY_EXISTS", "같은 유형의 면접이 이미 있습니다.");
            }
        }
        Interview interview = newInterview(candidate, request.getType(), maxSeq + 1);
        if (request.getMode() != null) {
            interview.setMode(request.getMode());
        }
        if (request.getDurationMinutes() != null) {
            interview.setDurationMinutes(request.getDurationMinutes());
        }
        if (request.getScheduledAt() != null) {
            interview.setScheduledAt(Times.toLocal(request.getScheduledAt()));
            interview.setStatus(InterviewStatus.SCHEDULED);
        }
        return assembler.toDto(interviewRepository.save(interview));
    }

    /** 트랙별 기본값으로 채운 새 면접(저장 전). 신입은 대면 30분, 경력은 화상 60분. */
    public static Interview newInterview(Candidate candidate, InterviewType type, int seq) {
        Interview interview = new Interview();
        interview.setCandidateId(candidate.getId());
        interview.setRecruitmentId(candidate.getRecruitmentId());
        interview.setTrack(candidate.getTrack());
        interview.setType(type);
        interview.setSeq(seq);
        boolean newGrad = candidate.getTrack() == Track.NEW_GRAD;
        interview.setMode(newGrad ? InterviewMode.OFFLINE : InterviewMode.ONLINE);
        interview.setDurationMinutes(newGrad ? 30 : 60);
        interview.setStatus(InterviewStatus.WAITING);
        interview.setCreatedAt(Times.now());
        return interview;
    }

    @Transactional
    public InterviewDto patch(Long id, InterviewRequests.Patch request, CurrentUser user) {
        Interview interview = access.requireWritable(id, user);
        if (request.getMode() != null) {
            interview.setMode(request.getMode());
        }
        if (request.getDurationMinutes() != null) {
            interview.setDurationMinutes(request.getDurationMinutes());
        }
        if (request.getScheduledAt() != null) {
            interview.setScheduledAt(Times.toLocal(request.getScheduledAt()));
            if (interview.getStatus() == InterviewStatus.WAITING && request.getStatus() == null) {
                interview.setStatus(InterviewStatus.SCHEDULED);
            }
        }
        if (request.getStatus() != null) {
            interview.setStatus(request.getStatus());
        }
        return assembler.toDto(interview);
    }

    /** 수동 지정: 기존 배정을 모두 지우고 주어진 면접관으로 바꾼다(matchedBy = MANUAL). */
    @Transactional
    public InterviewDto setInterviewers(Long id, List<Long> interviewerIds) {
        Interview interview = access.requireExisting(id);
        Set<Long> unique = new LinkedHashSet<Long>(interviewerIds);
        Map<Long, UserAccount> users = userService.mapByIds(unique);
        for (Long userId : unique) {
            UserAccount u = users.get(userId);
            if (u == null || !u.isActive()) {
                throw ApiException.notFound("USER_NOT_FOUND", "면접관을 찾을 수 없습니다: " + userId);
            }
        }
        assigneeRepository.deleteByInterviewId(id);
        for (Long userId : unique) {
            assigneeRepository.save(new InterviewAssignee(id, userId, MatchedBy.MANUAL));
        }
        return assembler.toDto(interview);
    }

    /**
     * 이 면접의 페르소나를 고른다(API 명세 2.5, 2.12). 배정된 면접관 또는 ADMIN.
     * 방이 LIVE·ENDED 면 바꿀 수 없다. 적용되는 페르소나가 실제로 바뀌었고 사전 리포트가 이미 있으면
     * 새 페르소나로 다시 만드는 작업을 접수한다(만드는 중이면 그대로 둔다).
     */
    @Transactional
    public InterviewDto setPersona(Long id, Long personaId, CurrentUser user) {
        Interview interview = access.requireWritable(id, user);
        if (interviewPersonas.isLocked(id)) {
            throw ApiException.conflict("PERSONA_LOCKED", "면접을 시작한 뒤에는 페르소나를 바꿀 수 없습니다.");
        }
        Persona chosen = personaService.requireAvailable(personaId);
        Persona before = interviewPersonas.effective(interview);
        interview.setPersonaId(chosen.getId());
        if (before == null || !before.getId().equals(chosen.getId())) {
            briefingService.regenerateIfExists(id);
        }
        return assembler.toDto(interview);
    }

    @Transactional
    public void changeStatus(Long interviewId, InterviewStatus status) {
        access.requireExisting(interviewId).setStatus(status);
    }
}
