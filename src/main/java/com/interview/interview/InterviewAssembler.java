package com.interview.interview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.interview.briefing.Briefing;
import com.interview.briefing.BriefingRepository;
import com.interview.candidate.Candidate;
import com.interview.candidate.CandidateRepository;
import com.interview.common.Chunks;
import com.interview.common.Times;
import com.interview.job.AiJob;
import com.interview.job.AiJobRepository;
import com.interview.job.JobStatus;
import com.interview.job.JobType;
import com.interview.report.Report;
import com.interview.report.ReportRepository;
import com.interview.room.Room;
import com.interview.room.RoomRepository;
import com.interview.user.UserAccount;
import com.interview.user.UserService;

import lombok.RequiredArgsConstructor;

/**
 * Interview 엔티티 묶음을 API 명세의 Interview 응답으로 바꾼다.
 * 목록에서 N+1 조회가 나지 않도록 딸린 데이터(면접자, 면접관, 방, 브리핑, 보고서)를 한꺼번에 읽는다.
 */
@Component
@RequiredArgsConstructor
public class InterviewAssembler {

    private final CandidateRepository candidateRepository;
    private final InterviewAssigneeRepository assigneeRepository;
    private final UserService userService;
    private final RoomRepository roomRepository;
    private final BriefingRepository briefingRepository;
    private final ReportRepository reportRepository;
    private final AiJobRepository aiJobRepository;

    public InterviewDto toDto(Interview interview) {
        return toDtos(Collections.singletonList(interview)).get(0);
    }

    public List<InterviewDto> toDtos(List<Interview> interviews) {
        List<InterviewDto> result = new ArrayList<InterviewDto>();
        if (interviews.isEmpty()) {
            return result;
        }
        List<Long> ids = new ArrayList<Long>();
        Set<Long> candidateIds = new HashSet<Long>();
        for (Interview i : interviews) {
            ids.add(i.getId());
            candidateIds.add(i.getCandidateId());
        }

        Map<Long, Candidate> candidates = new HashMap<Long, Candidate>();
        for (List<Long> chunk : Chunks.of(candidateIds)) {
            for (Candidate c : candidateRepository.findAllById(chunk)) {
                candidates.put(c.getId(), c);
            }
        }

        Map<Long, List<InterviewAssignee>> assignees = new HashMap<Long, List<InterviewAssignee>>();
        Map<Long, Room> rooms = new HashMap<Long, Room>();
        Map<Long, Briefing> briefings = new HashMap<Long, Briefing>();
        Map<Long, Report> latestReports = new HashMap<Long, Report>();
        Map<Long, AiJob> latestReportJobs = new HashMap<Long, AiJob>();
        Set<Long> userIds = new HashSet<Long>();
        for (List<Long> chunk : Chunks.of(ids)) {
            for (InterviewAssignee a : assigneeRepository.findByInterviewIdIn(chunk)) {
                List<InterviewAssignee> list = assignees.get(a.getInterviewId());
                if (list == null) {
                    list = new ArrayList<InterviewAssignee>();
                    assignees.put(a.getInterviewId(), list);
                }
                list.add(a);
                userIds.add(a.getUserId());
            }
            for (Room r : roomRepository.findByInterviewIdIn(chunk)) {
                rooms.put(r.getInterviewId(), r);
            }
            for (Briefing b : briefingRepository.findByInterviewIdIn(chunk)) {
                briefings.put(b.getInterviewId(), b);
            }
            for (Report r : reportRepository.findByInterviewIdIn(chunk)) {
                Report prev = latestReports.get(r.getInterviewId());
                if (prev == null || prev.getVersion() < r.getVersion()) {
                    latestReports.put(r.getInterviewId(), r);
                }
            }
            for (AiJob j : aiJobRepository.findByInterviewIdInAndType(chunk, JobType.REPORT)) {
                AiJob prev = latestReportJobs.get(j.getInterviewId());
                if (prev == null || prev.getId() < j.getId()) {
                    latestReportJobs.put(j.getInterviewId(), j);
                }
            }
        }
        Map<Long, UserAccount> users = userService.mapByIds(userIds);

        for (Interview i : interviews) {
            InterviewDto dto = new InterviewDto();
            dto.setId(i.getId());
            Candidate c = candidates.get(i.getCandidateId());
            if (c != null) {
                dto.setCandidate(new InterviewDto.CandidateRef(c.getId(), c.getName(), c.getApplicantNo(), c.getJobTitle()));
            }
            dto.setTrack(i.getTrack());
            dto.setType(i.getType());
            dto.setSeq(i.getSeq());
            dto.setMode(i.getMode());
            dto.setStatus(i.getStatus());
            dto.setScheduledAt(Times.toOffset(i.getScheduledAt()));
            dto.setDurationMinutes(i.getDurationMinutes());
            List<InterviewAssignee> list = assignees.get(i.getId());
            if (list != null) {
                for (InterviewAssignee a : list) {
                    UserAccount u = users.get(a.getUserId());
                    dto.getInterviewers().add(new InterviewDto.InterviewerRef(a.getUserId(),
                            u == null ? null : u.getName(), a.getMatchedBy()));
                }
            }
            Room room = rooms.get(i.getId());
            if (room != null) {
                dto.setRoomId(room.getId());
                dto.setRoomStatus(room.getStatus());
            }
            Briefing briefing = briefings.get(i.getId());
            dto.setBriefingStatus(briefing == null ? null : briefing.getStatus());
            Report report = latestReports.get(i.getId());
            AiJob reportJob = latestReportJobs.get(i.getId());
            // 보고서 상태: 가장 최근 REPORT 작업의 상태. 작업 기록 없이 보고서만 있으면(시드 등) SUCCEEDED.
            if (reportJob != null) {
                dto.setReportStatus(reportJob.getStatus());
            } else if (report != null) {
                dto.setReportStatus(JobStatus.SUCCEEDED);
            }
            dto.setReportBasis(report == null ? null : report.getBasis());
            result.add(dto);
        }
        return result;
    }
}
