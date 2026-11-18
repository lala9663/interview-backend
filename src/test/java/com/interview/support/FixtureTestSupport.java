package com.interview.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.interview.candidate.ApplicantType;
import com.interview.candidate.Candidate;
import com.interview.candidate.CandidateProfile;
import com.interview.candidate.CandidateRepository;
import com.interview.common.Times;
import com.interview.recruitment.Track;
import com.interview.user.UserAccountRepository;

/**
 * 테스트가 자기 데이터를 만들 때 쓰는 도우미: 새 채용과 직무(API), 면접자(repository, 면접자를 만드는 API 가 엑셀 업로드뿐이라서),
 * 면접과 면접관 지정(API). 테스트 클래스들이 DB 를 공유하므로 테스트마다 새 채용을 만들어 쓴다.
 */
public abstract class FixtureTestSupport extends ApiTestSupport {

    protected static final String JOB_CODE = "FX";

    @Autowired
    protected CandidateRepository candidateRepository;

    @Autowired
    protected UserAccountRepository userRepository;

    /** 새 채용과 직무 하나(코드 FX)를 만들고 채용 id 를 돌려준다. */
    protected long newRecruitment(String admin, Track track, String jobTitle) throws Exception {
        long id = api(HttpMethod.POST, admin, "/api/v1/recruitments",
                "{\"title\":\"" + jobTitle + " 채용\",\"track\":\"" + track + "\"}").expect(200).json.get("id").asLong();
        api(HttpMethod.POST, admin, "/api/v1/recruitments/" + id + "/jobs",
                "{\"jobCode\":\"" + JOB_CODE + "\",\"title\":\"" + jobTitle + "\",\"jdText\":\"" + jobTitle + " 업무를 합니다.\"}").expect(200);
        return id;
    }

    protected Candidate newCandidate(long recruitmentId, Track track, String applicantNo, String jobTitle,
            ApplicantType applicantType) {
        Candidate c = new Candidate();
        c.setRecruitmentId(recruitmentId);
        c.setApplicantNo(applicantNo);
        c.setName("가상" + applicantNo);
        c.setTrack(track);
        c.setJobCode(JOB_CODE);
        c.setJobTitle(jobTitle);
        c.setApplicantType(applicantType);
        CandidateProfile profile = new CandidateProfile();
        profile.getEssays().add(new CandidateProfile.TitledText("지원동기", applicantNo + " 의 지원동기 본문"));
        c.setProfile(profile);
        c.setCreatedAt(Times.now());
        return candidateRepository.save(c);
    }

    /** 면접을 만들고(WAITING) 주어진 사번의 면접관을 지정한다. 면접 id 를 돌려준다. */
    protected long newInterview(String admin, long candidateId, String type, String... employeeNos) throws Exception {
        long id = api(HttpMethod.POST, admin, "/api/v1/candidates/" + candidateId + "/interviews", "{\"type\":\"" + type + "\"}")
                .expect(200).json.get("id").asLong();
        if (employeeNos.length > 0) {
            StringBuilder ids = new StringBuilder();
            for (String employeeNo : employeeNos) {
                if (ids.length() > 0) {
                    ids.append(',');
                }
                ids.append(userRepository.findByEmployeeNo(employeeNo).get().getId());
            }
            api(HttpMethod.PUT, admin, "/api/v1/interviews/" + id + "/interviewers", "{\"interviewerIds\":[" + ids + "]}")
                    .expect(200);
        }
        return id;
    }

    /** 사전 리포트를 만들고 끝날 때까지 기다린다. */
    protected void generateBriefing(final String token, long interviewId) throws Exception {
        final long jobId = api(HttpMethod.POST, token, "/api/v1/interviews/" + interviewId + "/briefing/generate", null)
                .expect(202).json.get("jobId").asLong();
        await("브리핑 작업 " + jobId, () -> "SUCCEEDED".equals(apiGet(token, "/api/v1/ai-jobs/" + jobId).json.path("status").asText()));
    }
}
