package com.interview.systemstatus;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.interview.agent.AgentClient;
import com.interview.agent.dto.AgentDtos;
import com.interview.common.Times;
import com.interview.job.AiJob;
import com.interview.job.AiJobRepository;
import com.interview.job.JobStatus;
import com.interview.job.JobType;
import com.interview.room.RoomRepository;

import lombok.RequiredArgsConstructor;

/**
 * 시스템 상태(API 명세 2.13). 읽기 전용이며 job·room 의 집계 쿼리를 읽어 조립한다.
 *
 * - services: BACKEND(항상 OK), DB(조회가 되면 OK), AGENT(Agent 의 /health), STT·LLM(/health 의 sttStatus·llmStatus, 없으면 AGENT 를 따름)
 * - waits   : 작업 종류마다 최근에 성공한 작업이 접수부터 끝날 때까지 걸린 시간의 평균(초). 최근 5건 / 최근 50건
 * - queue   : 작업 종류마다 PENDING / RUNNING 수
 * - dailySessions: 오늘을 포함한 최근 7일에 시작한 방의 수
 * DB 를 읽지 못하면 DB 를 DOWN 으로 표시하고 waits·queue·dailySessions 는 빈 목록으로 돌려준다.
 * 응답은 5초 동안 기억한다(checkedAt 은 실제로 확인한 시각이다).
 */
@Service
@RequiredArgsConstructor
public class SystemStatusService {

    private static final Logger log = LoggerFactory.getLogger(SystemStatusService.class);
    private static final int RECENT = 5;
    private static final int DAYS = 7;
    private static final long CACHE_MILLIS = 5000L;

    private final AgentClient agentClient;
    private final AiJobRepository jobRepository;
    private final RoomRepository roomRepository;

    private final Object cacheLock = new Object();
    private volatile SystemStatusDtos.Status cached;
    private volatile long cachedAt;

    /**
     * 시스템 상태. 만든 응답을 5초 동안 기억한다(화면 여러 개가 주기적으로 물어도 그때마다 집계 쿼리를 돌리지 않게).
     * 5초는 만들기가 끝난 시각부터 세고, 낡은 순간에 요청이 몰려도 한 요청만 새로 만든다.
     */
    public SystemStatusDtos.Status status() {
        SystemStatusDtos.Status current = cached;
        if (current != null && System.currentTimeMillis() - cachedAt < CACHE_MILLIS) {
            return current;
        }
        synchronized (cacheLock) {
            current = cached;
            if (current != null && System.currentTimeMillis() - cachedAt < CACHE_MILLIS) {
                return current;
            }
            current = build();
            cached = current;
            cachedAt = System.currentTimeMillis();
            return current;
        }
    }

    /** 기억한 응답을 버린다(테스트용). */
    public void invalidate() {
        cached = null;
    }

    private SystemStatusDtos.Status build() {
        SystemStatusDtos.Status result = new SystemStatusDtos.Status();
        result.setCheckedAt(Times.toOffset(Times.now()));

        boolean dbOk = true;
        try {
            fillFromDb(result);
        } catch (RuntimeException e) {
            log.warn("시스템 상태: DB 조회 실패", e);
            dbOk = false;
            result.getWaits().clear();
            result.getQueue().clear();
            result.getDailySessions().clear();
        }

        AgentDtos.Health health = agentClient.health();
        ServiceStatus agent = health.isUp() ? ServiceStatus.OK : ServiceStatus.DOWN;
        ServiceStatus stt = health.isUp() ? ServiceStatus.parse(health.getSttStatus(), agent) : ServiceStatus.DOWN;
        ServiceStatus llm = health.isUp() ? ServiceStatus.parse(health.getLlmStatus(), agent) : ServiceStatus.DOWN;

        List<SystemStatusDtos.ServiceItem> services = result.getServices();
        services.add(new SystemStatusDtos.ServiceItem("BACKEND", "백엔드", "면접 기록과 평가 저장", ServiceStatus.OK, ""));
        services.add(new SystemStatusDtos.ServiceItem("DB", "DB", "면접 데이터 저장소", dbOk ? ServiceStatus.OK : ServiceStatus.DOWN,
                dbOk ? "" : "DB 를 읽을 수 없습니다."));
        services.add(new SystemStatusDtos.ServiceItem("AGENT", "Agent", "면접 방 실시간 처리", agent,
                agent == ServiceStatus.OK ? "" : "Agent 에 연결할 수 없습니다."));
        services.add(new SystemStatusDtos.ServiceItem("STT", "음성 인식", "면접 중의 말을 글로 옮김", stt, note(stt, agent, "음성 인식")));
        services.add(new SystemStatusDtos.ServiceItem("LLM", "LLM", "질문 · 꼬리질문 · 보고서", llm, note(llm, agent, "LLM")));
        return result;
    }

    private static String note(ServiceStatus status, ServiceStatus agent, String name) {
        if (status == ServiceStatus.OK) {
            return "";
        }
        if (agent == ServiceStatus.DOWN) {
            return "Agent 에 연결할 수 없어 상태를 알 수 없습니다.";
        }
        return status == ServiceStatus.DEGRADED ? name + " 응답이 느리거나 일부 요청이 실패하고 있습니다." : name + " 호출이 실패하고 있습니다.";
    }

    private void fillFromDb(SystemStatusDtos.Status result) {
        for (JobType type : JobType.values()) {
            List<AiJob> finished = jobRepository
                    .findTop50ByTypeAndStatusAndFinishedAtIsNotNullOrderByFinishedAtDescIdDesc(type, JobStatus.SUCCEEDED);
            if (!finished.isEmpty()) {
                result.getWaits().add(new SystemStatusDtos.WaitItem(type.name(), waitLabel(type),
                        averageSeconds(finished, RECENT), averageSeconds(finished, finished.size())));
            }
            result.getQueue().add(new SystemStatusDtos.QueueItem(type.name(), queueLabel(type),
                    jobRepository.countByTypeAndStatus(type, JobStatus.PENDING),
                    jobRepository.countByTypeAndStatus(type, JobStatus.RUNNING)));
        }

        LocalDate today = Times.today();
        Map<LocalDate, Integer> counts = new LinkedHashMap<LocalDate, Integer>();
        for (int n = DAYS - 1; n >= 0; n--) {
            counts.put(today.minusDays(n), 0);
        }
        for (LocalDateTime startedAt : roomRepository.findStartedAtBetween(today.minusDays(DAYS - 1).atStartOfDay(),
                today.plusDays(1).atStartOfDay())) {
            Integer count = counts.get(startedAt.toLocalDate());
            if (count != null) {
                counts.put(startedAt.toLocalDate(), count + 1);
            }
        }
        for (Map.Entry<LocalDate, Integer> e : counts.entrySet()) {
            result.getDailySessions().add(new SystemStatusDtos.DailySession(e.getKey(), e.getValue()));
        }
    }

    /** 앞에서부터 limit 건의, 접수(createdAt)부터 끝(finishedAt)까지 걸린 시간의 평균(초, 반올림). */
    private static long averageSeconds(List<AiJob> jobs, int limit) {
        long totalMillis = 0;
        int n = 0;
        for (AiJob job : jobs) {
            if (n >= limit) {
                break;
            }
            totalMillis += Math.max(0L, Duration.between(job.getCreatedAt(), job.getFinishedAt()).toMillis());
            n++;
        }
        return n == 0 ? 0 : Math.round(totalMillis / 1000.0 / n);
    }

    private static String waitLabel(JobType type) {
        switch (type) {
        case BRIEFING:
            return "사전 리포트와 질문 만들기";
        case AI_EVALUATION:
            return "AI 참고 평가";
        default:
            return "면접 보고서 초안";
        }
    }

    private static String queueLabel(JobType type) {
        switch (type) {
        case BRIEFING:
            return "사전 리포트와 질문";
        case AI_EVALUATION:
            return "AI 참고 평가";
        default:
            return "면접 보고서";
        }
    }
}
