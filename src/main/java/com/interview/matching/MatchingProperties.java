package com.interview.matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.interview.interview.InterviewType;
import com.interview.recruitment.Track;

import lombok.Getter;
import lombok.Setter;

/** app.matching.* 설정. */
@ConfigurationProperties(prefix = "app.matching")
@Getter
@Setter
public class MatchingProperties {

    /** 매칭 구현 선택. 지금은 jd 하나. */
    private String strategy = "jd";

    /** 면접 한 건에 배정할 면접관 수. */
    private int interviewersPerInterview = 2;

    /** 자동 매칭 때 면접이 하나도 없는 면접자에게 만들어 줄 면접(순서대로 1차, 2차 ...). 신입도 경력도 직무 → 인성이다. */
    private List<InterviewType> newGradPlan = new ArrayList<InterviewType>(
            Arrays.asList(InterviewType.JOB, InterviewType.PERSONALITY));
    private List<InterviewType> experiencedPlan = new ArrayList<InterviewType>(
            Arrays.asList(InterviewType.JOB, InterviewType.PERSONALITY));

    public List<InterviewType> plan(Track track) {
        return track == Track.EXPERIENCED ? experiencedPlan : newGradPlan;
    }
}
