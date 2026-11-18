package com.interview.interview;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.interview.recruitment.Track;

import lombok.Getter;
import lombok.Setter;

/**
 * app.live.* — 면접에서 실시간 기능(녹음, 전사, 꼬리질문 제안, 면접 뒤 AI 평가와 보고서)을 쓰는지(API 명세 2.5 의 liveEnabled).
 * 트랙별로 정한다. 꺼진 트랙의 면접은 방을 열고 시작·종료할 수 있지만 입장권을 주지 않고,
 * 종료해도 AI 작업을 시작하지 않으며, 사전 리포트를 만들 때 예상 꼬리질문을 함께 요청한다.
 */
@ConfigurationProperties(prefix = "app.live")
@Getter
@Setter
public class LiveProperties {

    /** 신입(대면) 면접. 기본은 끔. */
    private boolean newGradEnabled = false;

    /** 경력 면접. 기본은 켬. */
    private boolean experiencedEnabled = true;

    public boolean enabledFor(Track track) {
        return track == Track.EXPERIENCED ? experiencedEnabled : newGradEnabled;
    }
}
