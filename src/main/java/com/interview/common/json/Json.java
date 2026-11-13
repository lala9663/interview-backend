package com.interview.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** DB 의 CLOB(JSON 문자열) 변환에 쓰는 ObjectMapper. 웹 계층의 ObjectMapper 설정과 분리한다. */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Json() {
    }

    public static String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 직렬화 실패", e);
        }
    }

    public static <T> T read(String text, Class<T> type) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(text, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 역직렬화 실패", e);
        }
    }

    public static JsonNode tree(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 역직렬화 실패", e);
        }
    }

    public static JsonNode toTree(Object value) {
        return value == null ? null : MAPPER.valueToTree(value);
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode array() {
        return MAPPER.createArrayNode();
    }
}
