package com.interview.interview;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.interview.candidate.Candidate;
import com.interview.candidate.CandidateRepository;
import com.interview.common.ApiException;
import com.interview.persona.Persona;
import com.interview.recruitment.JobPosting;
import com.interview.recruitment.RecruitmentService;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 면접 한 건 + 면접자 + 직무(JD) + 적용되는 페르소나 + 실시간 기능 사용 여부. Agent 요청을 만들 때 공통으로 필요한 묶음. */
@Getter
public class InterviewContext {

    private final Interview interview;
    private final Candidate candidate;
    private final String jobTitle;
    private final String jdText;
    /** 이 면접에 적용되는 페르소나. 쓰는 페르소나가 하나도 없으면 null. */
    private final Persona persona;
    /** 이 면접에서 실시간 기능을 쓰는가(app.live.*). */
    private final boolean liveEnabled;

    public InterviewContext(Interview interview, Candidate candidate, String jobTitle, String jdText, Persona persona,
            boolean liveEnabled) {
        this.interview = interview;
        this.candidate = candidate;
        this.jobTitle = jobTitle;
        this.jdText = jdText;
        this.persona = persona;
        this.liveEnabled = liveEnabled;
    }

    @Component
    @RequiredArgsConstructor
    public static class Loader {

        private final InterviewAccess access;
        private final CandidateRepository candidateRepository;
        private final RecruitmentService recruitmentService;
        private final InterviewPersonas interviewPersonas;
        private final LiveProperties liveProps;

        @Transactional(readOnly = true)
        public InterviewContext load(Long interviewId) {
            Interview interview = access.requireExisting(interviewId);
            Candidate candidate = candidateRepository.findById(interview.getCandidateId())
                    .orElseThrow(() -> ApiException.notFound("CANDIDATE_NOT_FOUND", "면접자를 찾을 수 없습니다."));
            JobPosting job = recruitmentService.findJob(interview.getRecruitmentId(), candidate.getJobCode());
            String jobTitle = job != null ? job.getTitle() : candidate.getJobTitle();
            String jdText = job == null || job.getJdText() == null ? "" : job.getJdText();
            return new InterviewContext(interview, candidate, jobTitle, jdText, interviewPersonas.effective(interview),
                    liveProps.enabledFor(interview.getTrack()));
        }
    }
}
