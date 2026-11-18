package com.interview;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.auth.AppSecurityProperties;
import com.interview.job.AiJob;
import com.interview.job.AiJobRecovery;
import com.interview.job.AiJobRepository;
import com.interview.job.JobConfig;
import com.interview.job.JobStatus;
import com.interview.job.JobType;
import com.interview.common.Times;
import com.interview.support.ApiTestSupport;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

/** 인증·권한 규칙, 파일 단위 업로드 오류, 재시작 복구. */
class SecurityAndUploadErrorTest extends ApiTestSupport {

    @Autowired
    private AppSecurityProperties securityProps;

    @Autowired
    private AiJobRepository jobRepository;

    @Autowired
    private AiJobRecovery recovery;

    @Autowired
    private JobConfig.WorkerId workerId;

    @Test
    void healthIsOpenAndEverythingElseNeedsToken() throws Exception {
        JsonNode health = apiGet(null, "/api/v1/health").expect(200).json;
        assertThat(health.get("status").asText()).isEqualTo("UP");
        assertThat(health.get("agent").asText()).isEqualTo("UP");

        assertThat(apiGet(null, "/api/v1/recruitments").expect(401).json.get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(apiGet("not-a-token", "/api/v1/me").expect(401).json.get("code").asText()).isEqualTo("UNAUTHORIZED");

        String expired = Jwts.builder().setSubject("1").claim("employeeNo", "E1001").claim("name", "x").claim("role", "ADMIN")
                .setExpiration(new Date(System.currentTimeMillis() - 60000L))
                .signWith(Keys.hmacShaKeyFor(securityProps.getJwtSecret().getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        assertThat(apiGet(expired, "/api/v1/me").expect(401).json.get("code").asText()).isEqualTo("TOKEN_EXPIRED");

        assertThat(api(HttpMethod.POST, null, "/api/v1/auth/dev-login", "{\"employeeNo\":\"NOPE\"}").expect(401).json.get("code")
                .asText()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void refreshRotatesAndLogoutRevokes() throws Exception {
        JsonNode token = api(HttpMethod.POST, null, "/api/v1/auth/dev-login", "{\"employeeNo\":\"E1002\"}").expect(200).json;
        assertThat(token.get("expiresIn").asInt()).isEqualTo(1800);
        assertThat(token.get("user").get("name").asText()).isEqualTo("박준호");
        assertThat(token.get("user").get("jobCodes").toString()).contains("SW");
        String refresh = token.get("refreshToken").asText();

        JsonNode renewed = api(HttpMethod.POST, null, "/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}").expect(200).json;
        assertThat(apiGet(renewed.get("accessToken").asText(), "/api/v1/me").expect(200).json.get("employeeNo").asText())
                .isEqualTo("E1002");
        // 한 번 쓴 refresh 토큰은 다시 못 쓴다.
        api(HttpMethod.POST, null, "/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}").expect(401);

        String refresh2 = renewed.get("refreshToken").asText();
        api(HttpMethod.POST, null, "/api/v1/auth/logout", "{\"refreshToken\":\"" + refresh2 + "\"}").expect(200);
        api(HttpMethod.POST, null, "/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh2 + "\"}").expect(401);
    }

    @Test
    void adminOnlyEndpointsRejectInterviewers() throws Exception {
        String admin = login("E1001");
        String interviewer = login("E1003");
        Long recruitmentId = newGradRecruitmentId(admin);

        assertThat(api(HttpMethod.POST, interviewer, "/api/v1/recruitments", "{\"title\":\"x\",\"track\":\"NEW_GRAD\"}").expect(403).json
                .get("code").asText()).isEqualTo("FORBIDDEN");
        api(HttpMethod.POST, interviewer, "/api/v1/recruitments/" + recruitmentId + "/assignments/auto", null).expect(403);
        api(HttpMethod.POST, interviewer, "/api/v1/recruitments/" + recruitmentId + "/jobs",
                "{\"jobCode\":\"Z\",\"title\":\"z\",\"jdText\":\"\"}").expect(403);
        api(HttpMethod.POST, interviewer, "/api/v1/question-bank", "{\"text\":\"질문\"}").expect(403);
        upload(interviewer, recruitmentId, "x.xlsx", new byte[] { 1, 2, 3 }).expect(403);
        // 업로드는 올리기뿐 아니라 이력, 한 건 조회, 샘플 내려받기도 관리자만 한다.
        apiGet(interviewer, "/api/v1/recruitments/" + recruitmentId + "/uploads").expect(403);
        apiGet(interviewer, "/api/v1/uploads/1").expect(403);
        apiGet(interviewer, "/api/v1/uploads/template").expect(403);
        // 읽기는 된다.
        apiGet(interviewer, "/api/v1/recruitments/" + recruitmentId + "/candidates").expect(200);
        apiGet(interviewer, "/api/v1/question-bank?jobCode=SW").expect(200);

        // ADMIN 은 된다.
        JsonNode created = api(HttpMethod.POST, admin, "/api/v1/recruitments",
                "{\"title\":\"테스트 채용\",\"track\":\"EXPERIENCED\",\"startDate\":\"2026-11-01\",\"endDate\":\"2026-12-31\"}").expect(200).json;
        long newRecruitmentId = created.get("id").asLong();
        JsonNode job = api(HttpMethod.POST, admin, "/api/v1/recruitments/" + newRecruitmentId + "/jobs",
                "{\"jobCode\":\"SW\",\"title\":\"SW\",\"jdText\":\"JD\"}").expect(200).json;
        assertThat(api(HttpMethod.PUT, admin, "/api/v1/jobs/" + job.get("id").asLong(), "{\"title\":\"SW2\",\"jdText\":\"JD2\"}")
                .expect(200).json.get("jdText").asText()).isEqualTo("JD2");
        api(HttpMethod.POST, admin, "/api/v1/recruitments/" + newRecruitmentId + "/jobs",
                "{\"jobCode\":\"SW\",\"title\":\"SW\"}").expect(409);
        assertThat(apiGet(admin, "/api/v1/recruitments/" + newRecruitmentId).expect(200).json.get("candidateCount").asInt()).isZero();
        api(HttpMethod.POST, admin, "/api/v1/recruitments", "{\"track\":\"NEW_GRAD\"}").expect(400);
        apiGet(admin, "/api/v1/candidates/99999999").expect(404);
    }

    @Test
    void interviewerCanWriteOnlyAssignedInterviews() throws Exception {
        String admin = login("E1001");
        // 시드: 경력 1번(서지훈)의 인성 면접(2차)에는 E1002(박준호), E1004(최민재)가 배정돼 있다. E1005(정유진)는 아니다.
        long experiencedId = apiGet(admin, "/api/v1/recruitments?track=EXPERIENCED").expect(200).json.get("items").get(0).get("id").asLong();
        long candidateId = apiGet(admin, "/api/v1/recruitments/" + experiencedId + "/candidates?q=EXP-2026-0001").expect(200).json
                .get("items").get(0).get("id").asLong();
        JsonNode progress = apiGet(admin, "/api/v1/candidates/" + candidateId + "/progress").expect(200).json;
        assertThat(progress.get("items").get(0).get("status").asText()).isEqualTo("DONE");
        assertThat(progress.get("items")).hasSize(2);
        assertThat(progress.get("items").get(0).get("type").asText()).isEqualTo("JOB");
        assertThat(progress.get("items").get(1).get("type").asText()).isEqualTo("PERSONALITY");
        assertThat(progress.get("items").get(1).get("status").asText()).isEqualTo("SCHEDULED");
        long interviewId = progress.get("items").get(1).get("interviewId").asLong();

        String outsider = login("E1005");
        assertThat(api(HttpMethod.POST, outsider, "/api/v1/interviews/" + interviewId + "/room", null).expect(403).json.get("code")
                .asText()).isEqualTo("FORBIDDEN");
        api(HttpMethod.PUT, outsider, "/api/v1/interviews/" + interviewId + "/my-evaluation", "{\"memo\":\"x\"}").expect(403);
        api(HttpMethod.POST, outsider, "/api/v1/interviews/" + interviewId + "/questions", "{\"text\":\"x\"}").expect(403);
        // 읽기는 된다.
        apiGet(outsider, "/api/v1/interviews/" + interviewId).expect(200);

        String assigned = login("E1002");
        long roomId = api(HttpMethod.POST, assigned, "/api/v1/interviews/" + interviewId + "/room", null).expect(200).json.get("id")
                .asLong();
        api(HttpMethod.POST, outsider, "/api/v1/rooms/" + roomId + "/ticket", "{\"audio\":false}").expect(403);
        api(HttpMethod.POST, outsider, "/api/v1/rooms/" + roomId + "/start", null).expect(403);
        api(HttpMethod.POST, assigned, "/api/v1/rooms/" + roomId + "/ticket", "{\"audio\":false}").expect(200);
        api(HttpMethod.PUT, assigned, "/api/v1/interviews/" + interviewId + "/my-evaluation", "{\"memo\":\"메모\"}").expect(200);

        // mine=true 는 내가 배정된 면접만
        JsonNode mine = apiGet(outsider, "/api/v1/interviews?mine=true&size=200").expect(200).json;
        for (JsonNode i : mine.get("items")) {
            boolean found = false;
            for (JsonNode who : i.get("interviewers")) {
                found = found || "정유진".equals(who.get("name").asText());
            }
            assertThat(found).isTrue();
        }
        // ADMIN 의 수동 지정
        JsonNode manual = api(HttpMethod.PUT, admin, "/api/v1/interviews/" + interviewId + "/interviewers",
                "{\"interviewerIds\":[" + apiGet(outsider, "/api/v1/me").json.get("id").asLong() + "]}").expect(200).json;
        assertThat(manual.get("interviewers")).hasSize(1);
        assertThat(manual.get("interviewers").get(0).get("matchedBy").asText()).isEqualTo("MANUAL");
        api(HttpMethod.POST, outsider, "/api/v1/rooms/" + roomId + "/ticket", "{\"audio\":false}").expect(200);
    }

    @Test
    void internalApiNeedsInternalKey() throws Exception {
        assertThat(apiGet(null, "/internal/v1/rooms/1/context").expect(401).json.get("code").asText()).isEqualTo("UNAUTHORIZED");
        // 사용자 토큰으로는 내부 API 에 못 들어간다.
        apiGet(login("E1001"), "/internal/v1/rooms/1/context").expect(401);
        assertThat(internal(HttpMethod.GET, "/internal/v1/rooms/99999999/context", null).expect(404).json.get("code").asText())
                .isEqualTo("ROOM_NOT_FOUND");
        internal(HttpMethod.PUT, "/internal/v1/rooms/99999999/refined-transcript", "{\"items\":[]}").expect(400);
    }

    @Test
    void unreadableFileIsReportedWithDrmHint() throws Exception {
        String admin = login("E1001");
        Long recruitmentId = newGradRecruitmentId(admin);
        JsonNode error = upload(admin, recruitmentId, "drm.xlsx", "이것은 엑셀이 아닙니다(DRM 으로 암호화된 파일을 흉내 냄)".getBytes(StandardCharsets.UTF_8))
                .expect(400).json;
        assertThat(error.get("code").asText()).isEqualTo("FILE_UNREADABLE");
        assertThat(error.get("message").asText()).contains("DRM");
        // 헤더만 있는 양식은 면접자 행이 없다.
        byte[] empty = download(admin, "/api/v1/uploads/template?empty=true");
        assertThat(upload(admin, recruitmentId, "empty.xlsx", empty).expect(400).json.get("code").asText()).isEqualTo("FILE_EMPTY");
    }

    @Test
    void unfinishedJobsOfThisWorkerFailOnRestart() throws Exception {
        String admin = login("E1001");
        long interviewId = apiGet(admin, "/api/v1/interviews?size=1").expect(200).json.get("items").get(0).get("id").asLong();
        AiJob mine = jobRepository.save(job(interviewId, workerId.get()));
        AiJob others = jobRepository.save(job(interviewId, "another-server"));

        recovery.run(null);

        AiJob failed = jobRepository.findById(mine.getId()).get();
        assertThat(failed.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.getErrorCode()).isEqualTo("SERVER_RESTARTED");
        // 다른 서버가 맡은 작업은 건드리지 않는다.
        assertThat(jobRepository.findById(others.getId()).get().getStatus()).isEqualTo(JobStatus.RUNNING);
        jobRepository.deleteById(others.getId());
    }

    private static AiJob job(long interviewId, String worker) {
        AiJob job = new AiJob();
        job.setType(JobType.REPORT);
        job.setStatus(JobStatus.RUNNING);
        job.setInterviewId(interviewId);
        job.setWorkerId(worker);
        job.setCreatedAt(Times.now());
        job.setStartedAt(Times.now());
        return job;
    }
}
