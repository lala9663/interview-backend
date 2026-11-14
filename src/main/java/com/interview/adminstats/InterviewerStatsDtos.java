package com.interview.adminstats;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import lombok.Getter;
import lombok.Setter;

/** GET /admin/interviewer-stats 응답(계약 2.11). 제출된 평가만 집계한다. */
public final class InterviewerStatsDtos {

    private InterviewerStatsDtos() {
    }

    @Getter
    @Setter
    public static class Response {
        private OffsetDateTime generatedAt;
        private Overall overall = new Overall();
        private List<Item> items = new ArrayList<Item>();
    }

    @Getter
    @Setter
    public static class Overall {
        /** 제출된 평가 수. */
        private long evaluationCount;
        /** 제출된 평가의 항목 점수 전체의 평균·모표준편차. */
        private Double meanScore;
        private Double stdDev;
    }

    @Getter
    public static class InterviewerRef {
        private final Long id;
        private final String name;
        private final String department;

        public InterviewerRef(Long id, String name, String department) {
            this.id = id;
            this.name = name;
            this.department = department;
        }
    }

    @Getter
    @Setter
    public static class Item {
        private InterviewerRef interviewer;
        private long assignedCount;
        private long submittedCount;
        /** 면접은 끝났는데(EVALUATING·DONE) 제출하지 않은 배정 수. */
        private long pendingCount;
        private Double meanScore;
        private Double stdDev;
        private Double deviationFromOverall;
        private Double deviationFromPeers;
        private long peerComparedCount;
        private Map<String, Long> scoreDistribution = new TreeMap<String, Long>();
        private Map<String, Long> gradeDistribution = new TreeMap<String, Long>();
        private List<CriterionMean> byCriterion = new ArrayList<CriterionMean>();
    }

    @Getter
    public static class CriterionMean {
        private final String criterionCode;
        private final String criterionName;
        private final Double meanScore;

        public CriterionMean(String criterionCode, String criterionName, Double meanScore) {
            this.criterionCode = criterionCode;
            this.criterionName = criterionName;
            this.meanScore = meanScore;
        }
    }
}
