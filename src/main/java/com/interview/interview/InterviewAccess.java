package com.interview.interview;

import org.springframework.stereotype.Component;

import com.interview.auth.CurrentUser;
import com.interview.common.ApiException;

import lombok.RequiredArgsConstructor;

/**
 * 면접 단위 권한 규칙: 면접관은 자신이 배정된 면접의 방·평가·질문만 쓸 수 있다. ADMIN 은 전부.
 * 쓰기 엔드포인트의 서비스는 처음에 requireWritable 을 부른다.
 */
@Component
@RequiredArgsConstructor
public class InterviewAccess {

    private final InterviewRepository interviewRepository;
    private final InterviewAssigneeRepository assigneeRepository;

    public Interview requireExisting(Long interviewId) {
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> ApiException.notFound("INTERVIEW_NOT_FOUND", "면접을 찾을 수 없습니다."));
    }

    public Interview requireWritable(Long interviewId, CurrentUser user) {
        Interview interview = requireExisting(interviewId);
        if (!user.isAdmin() && !assigneeRepository.existsByInterviewIdAndUserId(interviewId, user.getId())) {
            throw ApiException.forbidden("배정된 면접이 아닙니다.");
        }
        return interview;
    }

    public boolean isAssigned(Long interviewId, Long userId) {
        return assigneeRepository.existsByInterviewIdAndUserId(interviewId, userId);
    }
}
