package com.interview.adminstats;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.interview.agent.AgentClient;
import com.interview.agent.AgentException;
import com.interview.agent.dto.AgentDtos;
import com.interview.common.Chunks;
import com.interview.common.Times;
import com.interview.interview.Interview;
import com.interview.interview.InterviewAssembler;
import com.interview.interview.InterviewDto;
import com.interview.interview.InterviewRepository;
import com.interview.interview.InterviewStatus;
import com.interview.job.AiJob;
import com.interview.job.AiJobRepository;
import com.interview.job.JobStatus;
import com.interview.job.JobType;
import com.interview.recruitment.RecruitmentService;
import com.interview.room.Room;
import com.interview.room.RoomRepository;
import com.interview.room.RoomStatus;
import com.interview.room.UtteranceRepository;

import lombok.RequiredArgsConstructor;

/**
 * 당일 운영 현황판(계약 2.11 의 /admin/operations).
 * 대상: 그 채용의 면접 가운데 예정 시각이 그 날짜인 것 + (날짜가 오늘이면) 지금 진행 중인 것.
 * 면접 수와 무관하게 조회 횟수가 일정하다(면접, 방, 전사 요약, 최근 작업을 묶음 조회).
 */
@Service
@RequiredArgsConstructor
public class OperationsService {

    private final RecruitmentService recruitmentService;
    private final InterviewRepository interviewRepository;
    private final InterviewAssembler interviewAssembler;
    private final RoomRepository roomRepository;
    private final UtteranceRepository utteranceRepository;
    private final AiJobRepository aiJobRepository;
    private final AgentClient agentClient;
    private final OperationsProperties props;
    private final PlatformTransactionManager transactionManager;

    public OperationsDtos.Response operations(Long recruitmentId, LocalDate dateParam) {
        // Agent 호출은 DB 트랜잭션 밖에서 먼저 한다(느리거나 죽어 있어도 커넥션을 잡고 기다리지 않게).
        AgentDtos.StatsResponse fetched;
        try {
            fetched = agentClient.stats();
        } catch (AgentException e) {
            fetched = null; // Agent 가 죽어 있어도 현황판은 DB 만으로 답한다(agent.status = DOWN).
        }
        final AgentDtos.StatsResponse stats = fetched;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        return tx.execute(status -> build(recruitmentId, dateParam, stats));
    }

    private OperationsDtos.Response build(Long recruitmentId, LocalDate dateParam, AgentDtos.StatsResponse stats) {
        recruitmentService.get(recruitmentId);
        LocalDate date = dateParam == null ? Times.today() : dateParam;
        LocalDateTime now = Times.now();
        boolean agentUp = stats != null;

        // ---- 대상 면접 ----
        Map<Long, Interview> interviews = new LinkedHashMap<Long, Interview>();
        for (Interview i : interviewRepository.findByRecruitmentIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                recruitmentId, date.atStartOfDay(), date.plusDays(1).atStartOfDay())) {
            interviews.put(i.getId(), i);
        }
        if (date.equals(Times.today())) {
            for (Interview i : interviewRepository.findByRecruitmentIdAndStatus(recruitmentId, InterviewStatus.IN_PROGRESS)) {
                interviews.put(i.getId(), i);
            }
        }
        List<Long> interviewIds = new ArrayList<Long>(interviews.keySet());

        // ---- 딸린 데이터(묶음 조회) ----
        Map<Long, Room> roomByInterview = new HashMap<Long, Room>();
        Map<Long, AiJob> latestJobByInterview = new HashMap<Long, AiJob>();
        for (List<Long> chunk : Chunks.of(interviewIds)) {
            for (Room room : roomRepository.findByInterviewIdIn(chunk)) {
                roomByInterview.put(room.getInterviewId(), room);
            }
            for (AiJob job : aiJobRepository.findLatestPerInterview(chunk, Arrays.asList(JobType.AI_EVALUATION, JobType.REPORT))) {
                latestJobByInterview.put(job.getInterviewId(), job);
            }
        }
        List<Long> roomIds = new ArrayList<Long>();
        for (Room room : roomByInterview.values()) {
            roomIds.add(room.getId());
        }
        Map<Long, Object[]> utteranceSummaryByRoom = new HashMap<Long, Object[]>();
        for (List<Long> chunk : Chunks.of(roomIds)) {
            for (Object[] row : utteranceRepository.summarizeByRoomIds(chunk)) {
                utteranceSummaryByRoom.put((Long) row[0], row);
            }
        }
        Map<Long, AgentDtos.RoomStat> agentRoomStats = new HashMap<Long, AgentDtos.RoomStat>();
        if (agentUp && stats.getRoomStats() != null) {
            for (AgentDtos.RoomStat rs : stats.getRoomStats()) {
                if (rs.getRoomId() != null) {
                    agentRoomStats.put(rs.getRoomId(), rs);
                }
            }
        }
        Map<Long, InterviewDto> dtoById = new HashMap<Long, InterviewDto>();
        for (InterviewDto dto : interviewAssembler.toDtos(new ArrayList<Interview>(interviews.values()))) {
            dtoById.put(dto.getId(), dto);
        }

        // ---- 응답 ----
        OperationsDtos.Response response = new OperationsDtos.Response();
        response.setDate(date);
        response.setGeneratedAt(Times.toOffset(now));
        response.setAgent(toAgent(stats));
        OperationsDtos.Summary summary = response.getSummary();
        final Map<OperationsDtos.Item, LocalDateTime> scheduledAtOf = new HashMap<OperationsDtos.Item, LocalDateTime>();
        for (Interview interview : interviews.values()) {
            Room room = roomByInterview.get(interview.getId());
            OperationsDtos.Item item = toItem(interview, dtoById.get(interview.getId()), room,
                    room == null ? null : utteranceSummaryByRoom.get(room.getId()),
                    room == null ? null : agentRoomStats.get(room.getId()), latestJobByInterview.get(interview.getId()),
                    agentUp, now);
            response.getItems().add(item);
            scheduledAtOf.put(item, interview.getScheduledAt());

            summary.setTotal(summary.getTotal() + 1);
            switch (interview.getStatus()) {
            case WAITING:
                summary.setWaiting(summary.getWaiting() + 1);
                break;
            case SCHEDULED:
                summary.setScheduled(summary.getScheduled() + 1);
                break;
            case IN_PROGRESS:
                summary.setLive(summary.getLive() + 1);
                break;
            case EVALUATING:
                summary.setEvaluating(summary.getEvaluating() + 1);
                break;
            case DONE:
                summary.setDone(summary.getDone() + 1);
                break;
            default:
                break; // CANCELED 는 total 에만 든다.
            }
            if (!item.getAlerts().isEmpty()) {
                summary.setAttention(summary.getAttention() + 1);
            }
        }
        // 주의가 있는 것 먼저, 그다음 예정 시각 순(일정이 없는 것은 맨 뒤), 같으면 id 순.
        Collections.sort(response.getItems(), new Comparator<OperationsDtos.Item>() {
            @Override
            public int compare(OperationsDtos.Item a, OperationsDtos.Item b) {
                boolean aa = !a.getAlerts().isEmpty();
                boolean ba = !b.getAlerts().isEmpty();
                if (aa != ba) {
                    return aa ? -1 : 1;
                }
                LocalDateTime sa = scheduledAtOf.get(a);
                LocalDateTime sb = scheduledAtOf.get(b);
                if (sa == null || sb == null) {
                    if (sa != sb) {
                        return sa == null ? 1 : -1;
                    }
                } else if (!sa.equals(sb)) {
                    return sa.compareTo(sb);
                }
                return a.getInterview().getId().compareTo(b.getInterview().getId());
            }
        });
        return response;
    }

    private OperationsDtos.Agent toAgent(AgentDtos.StatsResponse stats) {
        OperationsDtos.Agent agent = new OperationsDtos.Agent();
        if (stats == null) {
            agent.setStatus("DOWN");
            return agent;
        }
        agent.setStatus("UP");
        agent.setRooms(stats.getRooms());
        if (stats.getProviders() != null) {
            for (Map.Entry<String, AgentDtos.ProviderStat> e : stats.getProviders().entrySet()) {
                AgentDtos.ProviderStat p = e.getValue();
                agent.getProviders().add(new OperationsDtos.Provider(e.getKey(), p == null ? null : p.getHealth(),
                        p == null ? 0 : p.getWaiting()));
            }
        }
        return agent;
    }

    private OperationsDtos.Item toItem(Interview interview, InterviewDto dto, Room room, Object[] utteranceSummary,
            AgentDtos.RoomStat agentRoom, AiJob latestJob, boolean agentUp, LocalDateTime now) {
        OperationsDtos.Item item = new OperationsDtos.Item();
        item.setInterview(dto);
        LocalDateTime startedAt = room == null ? null : room.getStartedAt();
        LocalDateTime endedAt = room == null ? null : room.getEndedAt();
        boolean live = room != null && room.getStatus() == RoomStatus.LIVE && startedAt != null;
        boolean started = startedAt != null;
        item.setStartedAt(Times.toOffset(startedAt));
        item.setEndedAt(Times.toOffset(endedAt));

        // 진행 시간
        Long elapsed = null;
        if (started) {
            elapsed = minutes(startedAt, endedAt != null ? endedAt : now);
        }
        item.setElapsedMinutes(elapsed);

        // 지각: 시작했으면 예정 시각과 시작 시각의 차이, 아직이면 지금까지 늦은 시간
        LocalDateTime scheduledAt = interview.getScheduledAt();
        boolean waitingToStart = !started
                && (interview.getStatus() == InterviewStatus.WAITING || interview.getStatus() == InterviewStatus.SCHEDULED);
        if (scheduledAt != null && started) {
            item.setLateMinutes(minutes(scheduledAt, startedAt));
        } else if (scheduledAt != null && waitingToStart) {
            item.setLateMinutes(minutes(scheduledAt, now));
        }

        // 초과: 예정 길이를 넘긴 시간
        if (elapsed != null && interview.getDurationMinutes() != null) {
            item.setOvertimeMinutes(Math.max(0, elapsed - interview.getDurationMinutes()));
        }

        // 전사
        LocalDateTime lastUtteranceAt = null;
        if (utteranceSummary != null) {
            item.setUtteranceCount(((Number) utteranceSummary[1]).longValue());
            lastUtteranceAt = (LocalDateTime) utteranceSummary[2];
            if (lastUtteranceAt == null && utteranceSummary[3] != null && startedAt != null) {
                // 저장 시각이 없는 옛 데이터: 방 시작 + 마지막 발화의 끝 위치(ms)로 어림한다.
                lastUtteranceAt = startedAt.plusNanos(((Number) utteranceSummary[3]).longValue() * 1000000L);
            }
        }
        item.setLastUtteranceAt(Times.toOffset(lastUtteranceAt));
        if (live) {
            LocalDateTime base = lastUtteranceAt != null && lastUtteranceAt.isAfter(startedAt) ? lastUtteranceAt : startedAt;
            item.setSilentMinutes(minutes(base, now));
        }

        // Agent 가 아는 방 상태
        if (agentRoom != null) {
            item.setAudioConnected(agentRoom.getAudioConnected());
            item.setQueueDepth(agentRoom.getQueueDepth());
            item.setDroppedChunks(agentRoom.getDroppedChunks());
        }

        // 주의
        List<String> alerts = item.getAlerts();
        if (waitingToStart && scheduledAt != null && item.getLateMinutes() >= props.getNotStartedMinutes()) {
            alerts.add(OperationsDtos.NOT_STARTED);
        }
        if (live && item.getOvertimeMinutes() >= props.getOvertimeMinutes()) {
            alerts.add(OperationsDtos.OVERTIME);
        }
        if (live && Boolean.FALSE.equals(item.getAudioConnected())) {
            alerts.add(OperationsDtos.NO_AUDIO);
        }
        if (live && item.getSilentMinutes() >= props.getSilentMinutes()) {
            alerts.add(OperationsDtos.SILENT);
        }
        if (live && agentRoom != null
                && (agentRoom.getQueueDepth() >= props.getSttBacklogDepth() || agentRoom.getDroppedChunks() >= 1)) {
            alerts.add(OperationsDtos.STT_BACKLOG);
        }
        if (latestJob != null && latestJob.getStatus() == JobStatus.FAILED) {
            alerts.add(OperationsDtos.JOB_FAILED);
        }
        if (live && !agentUp) {
            alerts.add(OperationsDtos.AGENT_DOWN);
        }
        return item;
    }

    /** from → to 의 분(내림). 음수면 0. */
    private static long minutes(LocalDateTime from, LocalDateTime to) {
        return Math.max(0, Duration.between(from, to).toMinutes());
    }
}
