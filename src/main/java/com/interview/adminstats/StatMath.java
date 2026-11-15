package com.interview.adminstats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 집계에 쓰는 계산. API 명세 2.11: 비율은 0~1, 분모가 0이면 null, 소수 둘째 자리까지. */
final class StatMath {

    private StatMath() {
    }

    static Double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** 분모가 0이면 null. */
    static Double rate(long numerator, long denominator) {
        return denominator == 0 ? null : round2((double) numerator / denominator);
    }

    /** 반올림하지 않은 평균. 비어 있으면 null. */
    static Double rawMean(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (Double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    static Double mean(List<Double> values) {
        Double mean = rawMean(values);
        return mean == null ? null : round2(mean);
    }

    /** 모표준편차(N 으로 나눈다). 비어 있으면 null. */
    static Double stdDev(List<Double> values) {
        Double mean = rawMean(values);
        if (mean == null) {
            return null;
        }
        double sum = 0;
        for (Double v : values) {
            sum += (v - mean) * (v - mean);
        }
        return round2(Math.sqrt(sum / values.size()));
    }

    /** 중앙값(짝수 개면 가운데 두 값의 평균을 반올림). 비어 있으면 null. */
    static Long median(List<Long> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Long> sorted = new ArrayList<Long>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return Math.round((sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0);
    }

    static Long max(List<Long> values) {
        return values.isEmpty() ? null : Collections.max(values);
    }
}
