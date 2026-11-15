package com.interview.adminstats;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.interview.job.JobType;

import lombok.Getter;
import lombok.Setter;

/** GET /admin/quality 응답(계약 2.11). 비율·평균은 분모가 0이면 null. */
public final class QualityDtos {

    private QualityDtos() {
    }

    @Getter
    @Setter
    public static class Response {
        private OffsetDateTime generatedAt;
        /** 방이 끝난 면접 수. */
        private long endedInterviewCount;
        private ScoreGap scoreGap = new ScoreGap();
        private AiCoverage aiCoverage = new AiCoverage();
        private FollowUps followUps = new FollowUps();
        private Speaker speaker = new Speaker();
        private Questions questions = new Questions();
        private Reports reports = new Reports();
        private List<JobCount> jobs = new ArrayList<JobCount>();
    }

    @Getter
    @Setter
    public static class ScoreGap {
        private long comparedCount;
        /** AI - 면접관. 양수면 AI 가 후하다. */
        private Double meanDiff;
        private Double meanAbsDiff;
        private Double withinOneRate;
        private List<CriterionGap> byCriterion = new ArrayList<CriterionGap>();
    }

    @Getter
    @Setter
    public static class CriterionGap {
        private String criterionCode;
        private String criterionName;
        private long comparedCount;
        private Double aiMean;
        private Double interviewerMean;
        private Double meanDiff;
        private Double meanAbsDiff;
    }

    @Getter
    @Setter
    public static class AiCoverage {
        private long expected;
        private long scored;
        private Double rate;
    }

    @Getter
    @Setter
    public static class FollowUps {
        private long suggested;
        private long used;
        private Double adoptionRate;
        private long roomCount;
    }

    @Getter
    @Setter
    public static class Speaker {
        private long utteranceCount;
        private Map<String, Long> byMethod = new LinkedHashMap<String, Long>();
        private Double manualRate;
        private Double unknownRate;
    }

    @Getter
    @Setter
    public static class Questions {
        private long prepared;
        private long asked;
        private Double askedRate;
        private long autoMatched;
        private Double autoMatchedRate;
        /** 출처(JD, RESUME, BANK)별 사용률. 항상 이 순서로 세 항목. */
        private List<SourceUsage> bySource = new ArrayList<SourceUsage>();
    }

    @Getter
    @Setter
    public static class SourceUsage {
        private String source;
        private long prepared;
        private long asked;
        private Double askedRate;
    }

    @Getter
    @Setter
    public static class Reports {
        private long liveCount;
        private long refinedCount;
        private Long liveSecondsMedian;
        private Long liveSecondsMax;
        private Long refinedSecondsMedian;
        private Long refinedSecondsMax;
    }

    @Getter
    @Setter
    public static class JobCount {
        private JobType type;
        private long succeeded;
        private long failed;

        public JobCount(JobType type) {
            this.type = type;
        }
    }
}
