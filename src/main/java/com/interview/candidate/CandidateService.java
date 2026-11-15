package com.interview.candidate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.criteria.Predicate;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.interview.common.ApiException;
import com.interview.common.web.PageResponse;
import com.interview.interview.Interview;
import com.interview.interview.InterviewAssembler;
import com.interview.interview.InterviewDto;
import com.interview.interview.InterviewRepository;
import com.interview.interview.InterviewStages;
import com.interview.interview.InterviewStatus;
import com.interview.interview.InterviewType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewAssembler interviewAssembler;

    public Candidate get(Long id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("CANDIDATE_NOT_FOUND", "면접자를 찾을 수 없습니다."));
    }

    /**
     * 면접자 목록. status 와 type 은 "지금 차수" 면접의 상태·종류로 거른다.
     * 지금 차수는 DB 컬럼이 아니라 계산값이라, 채용 한 건의 면접자를 모두 읽은 뒤 메모리에서 거르고 자른다
     * (채용 한 건에 수천 명 수준까지를 가정).
     */
    public PageResponse<CandidateDtos.ListItem> list(final Long recruitmentId, final String jobCode, final String q,
            InterviewStatus status, InterviewType type, Integer page, Integer size) {
        Specification<Candidate> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("recruitmentId"), recruitmentId));
            if (jobCode != null && !jobCode.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("jobCode"), jobCode.trim()));
            }
            if (q != null && !q.trim().isEmpty()) {
                String like = "%" + q.trim() + "%";
                predicates.add(cb.or(cb.like(root.<String>get("name"), like),
                        cb.like(root.<String>get("applicantNo"), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<Candidate> candidates = candidateRepository.findAll(spec, Sort.by("applicantNo"));

        Map<Long, List<Interview>> byCandidate = new HashMap<Long, List<Interview>>();
        for (Interview i : interviewRepository.findByRecruitmentIdOrderByCandidateIdAscSeqAsc(recruitmentId)) {
            List<Interview> list = byCandidate.get(i.getCandidateId());
            if (list == null) {
                list = new ArrayList<Interview>();
                byCandidate.put(i.getCandidateId(), list);
            }
            list.add(i);
        }

        List<Candidate> filtered = new ArrayList<Candidate>();
        Map<Long, Interview> currentByCandidate = new HashMap<Long, Interview>();
        for (Candidate c : candidates) {
            List<Interview> list = byCandidate.get(c.getId());
            Interview current = list == null ? null : InterviewStages.current(list);
            if (status != null && (current == null || current.getStatus() != status)) {
                continue;
            }
            if (type != null && (current == null || current.getType() != type)) {
                continue;
            }
            if (current != null) {
                currentByCandidate.put(c.getId(), current);
            }
            filtered.add(c);
        }

        PageResponse<Candidate> sliced = PageResponse.slice(filtered, page, size);
        List<Interview> currentInterviews = new ArrayList<Interview>();
        for (Candidate c : sliced.getItems()) {
            Interview current = currentByCandidate.get(c.getId());
            if (current != null) {
                currentInterviews.add(current);
            }
        }
        Map<Long, InterviewDto> dtoByCandidate = new HashMap<Long, InterviewDto>();
        for (InterviewDto dto : interviewAssembler.toDtos(currentInterviews)) {
            dtoByCandidate.put(dto.getCandidate().getId(), dto);
        }

        List<CandidateDtos.ListItem> items = new ArrayList<CandidateDtos.ListItem>();
        for (Candidate c : sliced.getItems()) {
            CandidateDtos.ListItem item = new CandidateDtos.ListItem();
            item.setId(c.getId());
            item.setApplicantNo(c.getApplicantNo());
            item.setName(c.getName());
            item.setTrack(c.getTrack());
            item.setJobCode(c.getJobCode());
            item.setJobTitle(c.getJobTitle());
            InterviewDto current = dtoByCandidate.get(c.getId());
            if (current != null) {
                for (InterviewDto.InterviewerRef ref : current.getInterviewers()) {
                    item.getInterviewers().add(new CandidateDtos.PersonRef(ref.getId(), ref.getName()));
                }
                item.setBriefingStatus(current.getBriefingStatus());
                item.setCurrentStage(new CandidateDtos.Stage(current.getType(), current.getStatus()));
            }
            items.add(item);
        }
        return new PageResponse<CandidateDtos.ListItem>(items, sliced.getPage(), sliced.getSize(), sliced.getTotal());
    }

    public CandidateDtos.Detail detail(Long id) {
        Candidate c = get(id);
        CandidateDtos.Detail detail = new CandidateDtos.Detail();
        detail.setId(c.getId());
        detail.setApplicantNo(c.getApplicantNo());
        detail.setName(c.getName());
        detail.setTrack(c.getTrack());
        detail.setJobCode(c.getJobCode());
        detail.setJobTitle(c.getJobTitle());
        detail.setProfile(c.getProfile() == null ? new CandidateProfile() : c.getProfile());
        detail.setBlindedFields(new ArrayList<String>(c.getBlindedFields()));
        detail.setInterviews(interviewAssembler.toDtos(interviewRepository.findByCandidateIdOrderBySeqAsc(id)));
        return detail;
    }

    public CandidateDtos.Progress progress(Long id) {
        get(id);
        List<Interview> interviews = interviewRepository.findByCandidateIdOrderBySeqAsc(id);
        Interview current = InterviewStages.current(interviews);
        CandidateDtos.Progress progress = new CandidateDtos.Progress();
        progress.setCandidateId(id);
        if (current != null) {
            progress.setCurrentStage(new CandidateDtos.Stage(current.getType(), current.getStatus()));
        }
        for (InterviewDto dto : interviewAssembler.toDtos(interviews)) {
            CandidateDtos.ProgressItem item = new CandidateDtos.ProgressItem();
            item.setInterviewId(dto.getId());
            item.setSeq(dto.getSeq());
            item.setType(dto.getType());
            item.setMode(dto.getMode());
            item.setStatus(dto.getStatus());
            item.setScheduledAt(dto.getScheduledAt());
            item.setReportStatus(dto.getReportStatus());
            item.setCurrent(current != null && current.getId().equals(dto.getId()));
            progress.getItems().add(item);
        }
        return progress;
    }
}
