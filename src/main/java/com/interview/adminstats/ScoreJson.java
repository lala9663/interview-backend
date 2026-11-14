package com.interview.adminstats;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/** 평가의 점수 JSON([{ criterionCode, score, ... }])에서 점수가 있는 항목만 꺼낸다. AI 평가와 면접관 평가가 같은 모양이다. */
final class ScoreJson {

    private ScoreJson() {
    }

    /** 항목 코드 → 점수. 점수가 null 인 항목은 넣지 않는다. */
    static Map<String, Double> scores(JsonNode scores) {
        Map<String, Double> result = new LinkedHashMap<String, Double>();
        if (scores == null || !scores.isArray()) {
            return result;
        }
        for (JsonNode node : scores) {
            if (node.hasNonNull("criterionCode") && node.hasNonNull("score") && node.get("score").isNumber()) {
                result.put(node.get("criterionCode").asText(), node.get("score").asDouble());
            }
        }
        return result;
    }
}
